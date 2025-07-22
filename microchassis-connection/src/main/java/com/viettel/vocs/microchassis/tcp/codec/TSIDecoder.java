package com.viettel.vocs.microchassis.tcp.codec;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.datatype.FuseBoolean;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpBasedContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpBasedHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.CenterLBR;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import com.viettel.vocs.microchassis.connection.server.ServerChannelAttribute;
import com.viettel.vocs.microchassis.tcp.server.TcpBasedServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

import static com.viettel.vocs.common.CommonConfig.QueueConfig.DEFAULT_QUEUE_SIZE;

public abstract class TSIDecoder<BiDMsg extends Msg, CHandler extends TcpBasedHandler>
	extends TIDecoder<BiDMsg, CHandler> {
	private static final Logger logger = LogManager.getLogger(TSIDecoder.class);

	public static final String SERVER_AUTHEN_SERVICE_NAME = ConnectionManager.getMyDNS();
	private final ServerLimiter limiter;
	private final TcpBasedServer server;
	private final FuseBoolean established = new FuseBoolean(false);

	protected TSIDecoder(TcpBasedServer server, CHandler handlers, ServerLimiter limiter) {
		super(server.getEventHandler(), server.getId(), server.getConfig().channelConfiguration, server.getConfig().sendTimeoutMs, handlers, new ConcurrentHashMap<>(), ChassisThreadManager.tsHandlePool, server.getMsgCounter());
		this.server = server;
		this.limiter = limiter;
	}

	private boolean serverStopHandle(TcpBasedContext<BiDMsg> tcpCtx) {
		if (server.pause.get()) {
			tcpCtx.getCoMsg().reuse(MsgType.SERVER_STOP.getUrl());
			if (logger.isDebugEnabled())
				logger.debug("msgId {} stopController reject connection", tcpCtx.getInID());
			tcpCtx.send(tcpCtx);
			return false;
		}
		return true;
	}

	private boolean vduHandle(TcpBasedContext<BiDMsg> rCtx) { // set at server from header
		rCtx.getInMsg().headers.onHeaderNotNull(MsgHeader.VDU_NAME, (Handler<String>) headerValue ->
				// first ping added VDU_NAME to header -> all next on this connection will have VDU_NAME
			{
				Channel channel = rCtx.getChannel();
				if (channel != null) channel.attr(ServerChannelAttribute.attrClientVdu).set(headerValue);
			}
		);
		return true;
	}

	private boolean serverPingHandle(TcpBasedContext<BiDMsg> tcpCtx) {
		Msg inMsg = tcpCtx.getInMsg();
		if (inMsg.isPing() /* normal ping*/) { // ping of create request, has url create but msgId = ping
			tcpCtx.send(tcpCtx, f -> logger.info("TCP server send pong (empty body). id={}, tcpCtx={}", id, tcpCtx));
			return false;
		}
		return true;
	}

	private boolean createConnHandle(TcpBasedContext<BiDMsg> tcpCtx) {
		if (tcpCtx.getInMsg().isCreate() // for new version 4.2.0.3 and above
			|| tcpCtx.getInMsg().isPing() && established.isMelted()) { // for 4.0.21.10 and prior
			established.fuse(); // remove this line when depricated for 4.0.21.10 and prior
			long serverState = server.stateIfAllowedToConnect(tcpCtx.getChannel()); // add channel roi o cho CHANNEL_ACTIVE
			logger.debug("Receive createConn request. id={}, state={}, tcpCtx={}", id, serverState, tcpCtx);
			tcpCtx.getCoMsg().headers.set(MsgHeader.MESH_HEADER, String.valueOf(serverState)); // get nServer sau khi incr -> claimed 1 slot cho connect nay
			if (serverState < 0) { // ask and center
				switch (server.getMesh().mode) {
					case ASK_LIMIT:
						tcpCtx.send(f -> logger.info("DENIED CONNECTION from {}", tcpCtx.getEndpoint()), tcpCtx); // response ngay -1 neu khong mo duoc thay vi drop de timeout 3000
						/**
						 * always politely response, this -1 just mean recommend client to actively close, not force close from server
						 * 	all denied isolated into deniedConnections ChannelGroup, then wait for client to close themself
						 */
						return false;
					case CENTER_LIMIT:
						try {
							logger.info("Connection not allow to establish by Center {}", ((CenterLBR) server.getNegtor()).getServer("server_abm").targetMap);
						} catch (NullPointerException e) {
							logger.info("Connection not allow to establish by Center !!! not found negtor or targetMap");
						}
//						tcpCtx.outRelease(); // without reply
						tcpCtx.decompose(); // close if not obey handshake protocol in ask mode
						return false;
					case FULL_MESH:
					default:
						break; // allow
				}
			}
			logger.info("ACCEPT NEW CONNECTION from {}", tcpCtx.getEndpoint());
			// ask mode
			// khong drop connection o day duoc, vi
			// 1. Decode la sau khi lap connection, nhan goi tin moi drop connection-> khong dung ve mat ngu nghia
			// 2. Decode khong truy cap nguoc ve server de doc thong tin (port de ServerD.getInstance) duoc -> drop tai NettyServer CHANNEL_ACTIVE
			// go to ping handle then response by ping handle
		}
		return true;
	}


	private boolean overloadDrop(TcpBasedContext<BiDMsg> tcpCtx) {
		if (isServerOverLoad() && !tcpCtx.isInMgr()) {
			if (logger.isDebugEnabled())
				logger.debug("Drop message server by overload. id={}, tcpContext={}", id, tcpCtx);
			tcpCtx.sendDrop(f -> mon.serverNotifyDrop());
			return false;
		}
		return true;
	}

	private boolean isServerOverLoad() {
//		return ((ThreadPoolExecutor) handlerPool).getQueue().size() > 10_000; // CC 10k la rat lon, bthg echo server ton msg tang CC vot len cg chi den 3k
		int eventloopTaskQueueSize = server.workerTaskQueueSize();
		mon.overload(eventloopTaskQueueSize);
		return eventloopTaskQueueSize > DEFAULT_QUEUE_SIZE.get();
	}

	@Override
	protected boolean dropCheck(ChannelHandlerContext ctx, BiDMsg msg) {
		if (!msg.isMgr() && (handlerPool.getQueueSize() >= CommonConfig.QueueConfig.DEFAULT_QUEUE_SIZE.get() || !limiter.tryAcquire())) { // only drop normal msg
			TcpBasedContext<BiDMsg> tcpCtx = wrapCtx(ctx, msg);
			tcpCtx.sendDrop();
			handlerPool.submit(() -> { // post send drop handler
				// noti on pool threads
				mon.serverNotifyDrop();
				mon.fullDecodeQueueDrop(msg.getDecodeTime());
				TcpBasedHandler handler = getHandler();
				if (handler != null) handler.chassisDropHandle(msg, true);
			});
			return true;
		}
		return false;
	}

	@Deprecated
	private boolean serverDropHandle(TcpBasedContext<BiDMsg> rctx) {
		if (!limiter.tryAcquire() && !rctx.isInMgr()) { // only drop normal msg
			if (logger.isDebugEnabled())
				logger.debug("Drop message server. id={}, tcpContext={}", id, rctx);
			TcpBasedHandler handler = getHandler();
			if (handler != null) {
				rctx.sendDrop(f -> mon.serverNotifyDrop());
				handler.chassisDropHandle(rctx.getInMsg(), true);
			}
			rctx.decompose();
			return false;
		}
		return true;
	}

	@Override
	protected void decodeHandler(ChannelHandlerContext ctx, BiDMsg inMsg) { // khong check duoc sentTime serverPush
		TcpBasedContext<BiDMsg> tcpCtx = wrapCtx(ctx, inMsg);
		// client call url nao thi mac dinh reply ve url do, co the doi url neu can
		mon.inServerQueue(inMsg, handlerPool.getQueueSize(), TimeUtils.nowNano()); // mesh-log4j use info, app use error
		if (logger.isDebugEnabled())
			logger.debug("Server received Msg {} content length {} toStr {} hex {} and header {}", inMsg.getMessageId(), inMsg.getContent().length, inMsg, inMsg.contentHexString(), inMsg.headers());
		if (SERVER_AUTHEN_SERVICE_NAME != null && !SERVER_AUTHEN_SERVICE_NAME.isEmpty())
			tcpCtx.getCoMsg().headers.set(MsgHeader.SERVICE_NAME, SERVER_AUTHEN_SERVICE_NAME);
		if (!(
			// mgrHandle
//			serverStopHandle(tcpCtx) &&
			createConnHandle(tcpCtx) // always response with nServerConn event fullmesh
				&& serverPingHandle(tcpCtx) // place after createConnHandle to resonse valid first msg of create flow
// checked by dropCheck() //				&& serverDropHandle(pctx) // only trigger dropHandle if this server drop actively. In case of server send response or push to client, if client drop and response to server
//				&& overloadDrop(tcpCtx)  // actively call dropHandle without logging
				// msgHandle
				&& vduHandle(tcpCtx) // for server push later
				&& asyncHandleOrsentDropHandle(tcpCtx) // TcpHandler go here
		)) { // boolean true ~ done, false ~ drop // false o buoc nao return drop ngay
			// khong duoc return o day de tiep tuc doc cac msg khac trong buffer
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//        super.channelReadComplete(ctx); //To change body of generated methods, choose Tools | Templates.
		if (!config.fastFlush) ctx.flush();
	}
}
