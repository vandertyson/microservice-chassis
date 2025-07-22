package com.viettel.vocs.microchassis.tcp.codec;

import com.viettel.vocs.common.datatype.DataUnit;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpBasedContext;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpBasedHandler;
import com.viettel.vocs.microchassis.connection.MsgCountable;
import com.viettel.vocs.microchassis.connection.config.ChannelConfiguration;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.util.MonitorManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.viettel.vocs.microchassis.connection.event.ContextEvent.EventType.CHANNEL_ACTIVE;
import static com.viettel.vocs.microchassis.connection.event.ContextEvent.EventType.CHANNEL_INACTIVE;
import static com.viettel.vocs.microchassis.tcp.codec.Msg.logger;

class IncompleteFragmentRead extends DecoderException {
	public final int requiredLength;
	public final int fragmentLength;

	public IncompleteFragmentRead(int requiredLength, int fragmentLength) {
		super("Decode failed at msg length " + requiredLength + " on readable " + fragmentLength);
		this.requiredLength = requiredLength;
		this.fragmentLength = fragmentLength;
	}
}

public abstract class TIDecoder<BiDMsg extends Msg, CHandler extends TcpBasedHandler> extends ByteToMessageDecoder implements MsgCountable {
	public final Map<String, CompletableFuture> syncPromiseMap;

	protected final ChannelConfiguration config;

	protected final String id;
	protected final long sendTimeoutMs;
	protected final EventHandler eventHandler;
	@Getter protected final CHandler handler;
	protected final ThreadManager.PoolExecutor handlerPool;
	private final MsgCounter msgCounter;
	@Override
	public long getMsgCount() {
		return msgCounter.getMsgCount();
	}

	@Override
	public double getBytesCount(DataUnit unit) {
		return msgCounter.getBytesCount(unit);
	}
	static {
		logger.info("Create Decoder with {} threads for servers and {} threads for clients", ChassisConfig.PerformanceConfig.T_SERVER_HANDLEPOOL_CONFIG.CORE_POOL_SIZE, ChassisConfig.PerformanceConfig.T_CLIENT_HANDLEPOOL_CONFIG.CORE_POOL_SIZE);
	}


	protected TIDecoder(EventHandler eventHandler, String id, ChannelConfiguration config, long sendTimeoutMs, CHandler handler, Map<String, CompletableFuture> syncPromiseMap, ThreadManager.PoolExecutor handlePool, MsgCounter msgCounter) {
		this.id = id;
		this.eventHandler = eventHandler;
		this.sendTimeoutMs = sendTimeoutMs;
		this.config = config;
		this.msgCounter = msgCounter;
		this.syncPromiseMap = syncPromiseMap;
		this.handler = handler;
		this.handlerPool = handlePool;
	}

	/**
	 * define how message is build into BiDMsg from bytebuf then wrap into ReplyContext
	 *
	 * @param ctx
	 * @return
	 */
	public abstract TcpBasedContext<BiDMsg> wrapCtx(ChannelHandlerContext ctx, BiDMsg decodedMsg);

	private static int parseHeader(ByteBuf in, MsgHeader headers, int totalLenHeader) {
		try {
			int keySize = in.readInt();
			String key = in.readCharSequence(keySize, StandardCharsets.UTF_8).toString();
			int valueSize = in.readInt();
			String value = in.readCharSequence(valueSize, StandardCharsets.UTF_8).toString();
			headers.set(key, value);
			totalLenHeader += 8 + valueSize + keySize;
		} catch (Exception ex) {
			logger.error(ex, ex);
		}
		return totalLenHeader;
	}

	public BiDMsg decode(ByteBufAllocator allocator, ByteBuf in, int dataLength) {
		int lengOfDataUrl = in.readInt();
		String url = readStringFromBuf(in, lengOfDataUrl);

		int lengOfMsgUD = in.readInt();
		String msgID = readStringFromBuf(in, lengOfMsgUD);

		int totalLenHeader = 0;
		int headerMapSize = in.readInt();
		if (logger.isDebugEnabled())
			logger.debug("Decode inbound header. url={}, msgId={}, headers={}", url, msgID, headerMapSize);
		MsgHeader headers = new MsgHeader();
		if (headerMapSize > 0) {
			for (int i = 0; i < headerMapSize; i++) {
				totalLenHeader = parseHeader(in, headers, totalLenHeader);
			}
		}
		int bufferLen = dataLength - 12 - lengOfDataUrl - lengOfMsgUD - totalLenHeader;
		if (logger.isDebugEnabled()) {
			logger.debug("Decode inbound result. url={}, msgID={}, dataLen={}, bytesUrl={}, bytesMsgId={}, totalHeaderLen={}, msgContent={}",
				url, msgID, dataLength, lengOfDataUrl, lengOfMsgUD, totalLenHeader, bufferLen);
		}
		// ByteBuf in: is very long stream contain all data and rolling by Netty self release
		// decode step capture frames into separate BB, it is deep copy, then pass that bb into Msg as readonly
		// ref for original bb then end here, then we can release at BiDMsg readonlyBB anytime
		ByteBuf bb = allocator.buffer(bufferLen);
		in.readBytes(bb);
		return createDecodedMsg(bb, url, msgID, headers);
	}

	protected abstract BiDMsg createDecodedMsg(ByteBuf body, String url, String messageId, MsgHeader headers);


	protected boolean asyncHandleOrsentDropHandle(TcpBasedContext<BiDMsg> tcpCtx) {

//		if (handler != null) {
			try {
				BiDMsg inMsg = tcpCtx.getInMsg();
				if (inMsg.isPeerDrop()) {
					handler.chassisDropHandle(inMsg, true);
				} else {
					if(this instanceof TSIDecoder) mon.userSHandler(inMsg.getDecodeTime(), inMsg.readableBytes());
					else if(this instanceof TCIDecoder) mon.userCHandler(inMsg.getDecodeTime(), inMsg.readableBytes());
					handler.chassisHandle(tcpCtx);
				}
			} catch (Exception ex) {
				logger.error(ex, ex);
				tcpCtx.decompose();
			}
//		} else {
//			logger.error("User handler not found. Ignore msg. id={}, tcpCtx={}", id, tcpCtx);
//			tcpCtx.decompose();
//		}
		return false; // userHandler at tail of chain, return false to exit while of decode
	}

	protected String readStringMsgFromBuf(ByteBuf buf) {
		return readStringFromBuf(buf, buf.readInt());
	}

	protected String readStringFromBuf(ByteBuf buf, int length) {
		return buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
	}


	protected abstract void decodeHandler(ChannelHandlerContext ctx, BiDMsg msg);

	MonitorManager mon = MonitorManager.getInstance();
	protected abstract boolean dropCheck(ChannelHandlerContext ctx, BiDMsg msg);
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		/**
		 * can phai dem msg dang duoc netty xu ly, netty su dung LinkedBlockingQueue -> queue vo han,
		 * 	neu msg den qua nhanh -> heap tang do thread decode tang, leak chet
		 * 	phan doc dataLength rat nhanh nen khong can dem, chi dem o phan xu ly msg
		 */
		// Wait until the length prefix is available.
		int count = 0;
		int dataLength;
		try {
			dataLength = readMsgLength(in);
			msgCounter.stack(in);
		} catch (DecoderException ex) {
			return;
		}
		// ^^^ read for first full message
		try {
			// vvv if there was more than 1 msg in buffer in, continuously read by do while until left not enough or maxDecode
			do {
				BiDMsg msg = decode(ctx.alloc(), in, dataLength);
				msgCounter.stack(msg);
				if(logger.isDebugEnabled()) logger.info("Received body length "+ dataLength);
//						TimeUtils.waitSafeTick(); // neu troi cung duoc, uu tien thread xu ly nghiep vu hon la nhan them msg: natural backpressure by blocking
				if (!dropCheck(ctx, msg)) handlerPool.submit(() -> decodeHandler(ctx, msg)); // msg duoc tao tu buffer alloc moi, copy tu in, nen ko can retain //		tcpCtx.getInMsg().retain(); // in here msg refCnt = 2, move out of netty thread -> msg will be release
//					} else
//						decodeHandler(tcpCtx); // ping and create and mgrMsg indeed are sendSync and send -> recordSend // release early for other thread claim CC to send
//					} finally{ // do submit to threadpool nen ko decompose
//						if (config.decomposeCtxOnDone) tcpCtx.decompose();
//					}
				count++;
				in.markReaderIndex();
			}
			while ((count <= config.maxDecode) && in.readableBytes() >= 4 && (dataLength = in.readInt()) <= in.readableBytes());
			in.resetReaderIndex();
		} catch (Exception ex) { // other error
			logger.error(ex, ex);
		}
	}


	protected int readMsgLength(ByteBuf in) throws DecoderException {
		// Wait until the length prefix is available.
		if (in.readableBytes() < 4) throw new DecoderException("Decode failed at read msglength int");
		in.markReaderIndex();
		// Wait until the whole data is available.
		int dataLength = in.readInt();
		if (in.readableBytes() < dataLength) {
			int readable = in.readableBytes();
			in.resetReaderIndex();
			throw new IncompleteFragmentRead(dataLength, readable);
		}
		return dataLength;
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("Channel exception caught. id={}, ctx={}, cause={}",
			id, ctx.channel(), cause);
		logger.error(cause, cause);
		super.exceptionCaught(ctx, cause);
	}


	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		logger.info("USER TRIGGER {}", evt);
		super.userEventTriggered(ctx, evt); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx); //To change body of generated methods, choose Tools | Templates.

		if (eventHandler != null)
			eventHandler.triggerEvent(new ContextEvent(CHANNEL_INACTIVE, ctx));
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		if (eventHandler != null)
			eventHandler.triggerEvent(new ContextEvent(CHANNEL_ACTIVE, ctx));
	}
}
