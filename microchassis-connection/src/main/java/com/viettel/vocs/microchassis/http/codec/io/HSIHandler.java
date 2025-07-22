package com.viettel.vocs.microchassis.http.codec.io;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.datatype.DataUnit;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.connection.MsgCountable;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ServerCounter;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.metrics.InternalMetric;
import com.viettel.vocs.microchassis.util.MonitorManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import static com.viettel.vocs.microchassis.tcp.codec.Msg.logger;

interface HttpMsgDecoder {
	ThreadManager.PoolExecutor handlerPool = ChassisThreadManager.hsHandlerPool;

	default void httpDecode(ChannelHandlerContext ctx, HttpRequest msg) {
		ServerCounter counter = getCounter();
		if (counter != null) counter.recordReceive();
		if (logger.isDebugEnabled()) logger.info("Received body length " + msg.readableBytes());
//						TimeUtils.waitSafeTick(); // neu troi cung duoc, uu tien thread xu ly nghiep vu hon la nhan them msg: natural backpressure by blocking
		HttpServerContext hctx = wrapCtx(ctx, msg);
		if (!dropCheck(hctx)) {
			// do not merge 2 row above and bellow into 1
			if (hctx.isHttp2()) decodeHandle(hctx);
			else handlerPool.submit(() -> decodeHandle(hctx));
		}
	}

	MonitorManager mon = MonitorManager.getInstance();

	default boolean dropCheck(HttpServerContext hctx) {
		ServerLimiter limiter = getLimiter();
		if (!hctx.isInMgr() && (handlerPool.getQueueSize() >= CommonConfig.QueueConfig.DEFAULT_QUEUE_SIZE.get() || !limiter.tryAcquire())) { // only drop normal msg
			hctx.sendDrop();
			handlerPool.submit(() -> { // post send drop handler
				// noti on pool threads
				mon.serverNotifyDrop();
				mon.fullDecodeQueueDrop(hctx.getInMsg().getDecodeTime());
				HttpServerHandler handler = getHandler();
				if (handler != null) handler.chassisServerDropHandle(hctx, true);
			});
			return true;
		}
		return false;
	}

	private void decodeHandle(HttpServerContext hctx) {
		HttpRequest inMsg = hctx.getInMsg();
		mon.inServerQueue(inMsg, handlerPool.getQueueSize(), TimeUtils.nowNano()); // mesh-log4j use info, app use error
		if (logger.isDebugEnabled())
			logger.debug("Server received Msg {} content length {} toStr {} hex {} and header {}",
				inMsg.getMessageId(), inMsg.getContent().length, inMsg, inMsg.contentHexString(), inMsg.headers());

		if (!asyncHandleOrsentDropHandle(hctx)) { // Handler go here // boolean true ~ done, false ~ drop // false o buoc nao return drop ngay
			// khong duoc return o day de tiep tuc doc cac msg khac trong buffer
		}
	}

	private boolean asyncHandleOrsentDropHandle(HttpServerContext hctx) {
		HttpServerHandler handler = getHandler();
		try {
			HttpRequest inMsg = hctx.getInMsg();
			if (inMsg.isPeerDrop()) {
				handler.chassisDropHandle(inMsg, true);
			} else {
				mon.userSHandler(inMsg.getDecodeTime(), inMsg.readableBytes());
				handler.chassisHandle(hctx);
			}
		} catch (Exception ex) {
			logger.error(ex, ex);
			hctx.send(ex.getMessage(), HttpResponseStatus.INTERNAL_SERVER_ERROR, hctx);
		}
		return false;
	}

	@NotNull
	private static HttpServerContext wrapCtx(ChannelHandlerContext ctx, HttpRequest inMsg) {
		return new HttpServerContext(ctx, inMsg);
	}

	HttpServerHandler getHandler();

	ServerLimiter getLimiter();

	ServerCounter getCounter();
}

public abstract class HSIHandler extends SimpleChannelInboundHandler<FullHttpRequest> implements HttpMsgDecoder, MsgCountable {
	protected static final Logger logger = LogManager.getLogger(HSIHandler.class);
	@Setter
	protected Handler<Throwable> onErrorHandler;
	@Getter
	protected final ServerCounter counter;
	@Getter
	protected final ServerLimiter limiter;
	@Getter
	protected final HttpServerHandler handler;
	protected final EventHandler eventHandler;
	protected final MsgCounter msgCounter;

	@Override
	public long getMsgCount() {
		return msgCounter.getMsgCount();
	}

	@Override
	public double getBytesCount(DataUnit unit) {
		return msgCounter.getBytesCount(unit);
	}

	protected HSIHandler(@NotNull EventHandler eventHandler, HttpServerHandler handler, ServerCounter counter, ServerLimiter limiter, MsgCounter msgCounter) {
		this.eventHandler = eventHandler;
		this.handler = handler;
		this.counter = counter;
		this.limiter = limiter;
		this.msgCounter = msgCounter;
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx); //To change body of generated methods, choose Tools | Templates.
		eventHandler.triggerEvent(new ContextEvent(ContextEvent.EventType.CHANNEL_INACTIVE, ctx));
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx); //To change body of generated methods, choose Tools | Templates.
		eventHandler.triggerEvent(new ContextEvent(ContextEvent.EventType.CHANNEL_ACTIVE, ctx));
		NettyServer.serverMetric.incr(InternalMetric.Server.COUNT_HTTP_CONNECTION_UP);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		// TODO check why here empty
		// TODO check why this place not do anything
	}


	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		super.exceptionCaught(ctx, cause);
		ctx.close();
		if (onErrorHandler != null) onErrorHandler.handle(cause);
		logger.error(cause, cause);
	}
}
