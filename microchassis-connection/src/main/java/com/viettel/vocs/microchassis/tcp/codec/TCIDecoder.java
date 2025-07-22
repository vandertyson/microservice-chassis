package com.viettel.vocs.microchassis.tcp.codec;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpBasedHandler;
import com.viettel.vocs.microchassis.connection.client.ClientChannelAttribute;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.BackPressureConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.MonitorableConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static com.viettel.vocs.microchassis.base.ChassisConst.CustomHeader.inRqHeader;


/**
 * Bidirectional decoder for both req and res of Msg type
 */
public abstract class TCIDecoder<BiDMsg extends Msg, CHandler extends TcpBasedHandler> extends TIDecoder<BiDMsg, CHandler> {
	private static final Logger logger = LogManager.getLogger(TCIDecoder.class);
	private final AtomicReference<PeerCounter> counter;
	private final boolean to3rdParty;
	protected ConnectionMode connMode;


	protected TCIDecoder(EventHandler eventHandler, CHandler handlers, Map<String, CompletableFuture> syncPromiseMap, ClientConfiguration config, AtomicReference<PeerCounter> counterRef, AtomicReference<ConnectionMode> connModeRef, MsgCounter msgCounter) {
		super(eventHandler, config.id, config.channelConfiguration, config.sendTimeoutMs, handlers, syncPromiseMap, ChassisThreadManager.tcHandlePool, msgCounter);
		to3rdParty = config.to3rdParty;
		counter = counterRef;
		setMode(connModeRef.get());
	}

	public ConnectionMode setMode(ConnectionMode newMode) {
		connMode = newMode;
		return connMode;
	}

	@Override
	protected boolean dropCheck(ChannelHandlerContext ctx, BiDMsg msg) {
		if (msg.isServerStop()) {
			invokeServerDownEvent(ctx, msg.getMessageId());
			msg.decompose();
			return true; // isStop
		} else if (msg.isServerHeaderStop()) {
			invokeServerDownEvent(ctx, msg.getMessageId());
			// continue process
		}
		return false;
	}

	private void invokeServerDownEvent(ChannelHandlerContext ctx, String msgId) {
		ClientChannelAttribute.getConnection(ctx).setDeleteMarker(true);
		if (eventHandler != null)
			eventHandler.triggerEvent(new ContextEvent(ContextEvent.EventType.SERVER_DOWN, ctx));
		logger.info("Client receive server down signal on channel {} for server={} of msgId={}", ctx.channel(), id, msgId);
	}


	/**
	 * handle then response or drop, without return true for other else handle, stop here
	 */
	private boolean clientMgrMsgHandle(BiDMsg inMsg) {
		String strategyDrop = inMsg.headers.getString("strategyDrop");
		inMsg.headers.onHeaderNotNull(MsgHeader.LOAD_HEADER, (Handler<String>) load -> {
			if (connMode instanceof MonitorableConnectionMode)
				((MonitorableConnectionMode) connMode).updatePeerLoad(Integer.parseInt(load));
		});
		if (connMode instanceof BackPressureConnectionMode && strategyDrop != null)
			((BackPressureConnectionMode) connMode).peerStrategyFailStack.set(Integer.parseInt(strategyDrop));
		if (inMsg.isCreate()) {
			CompletableFuture remove = syncPromiseMap.remove(inMsg.getMessageId());
			logger.info("FIRST SHAKE msg={} header={} -> future {} from map {}", inMsg, inMsg.headers(), remove, System.identityHashCode(syncPromiseMap));
			return syncHandle(inMsg, remove);
		} else if (inMsg.isPing()) {
			CompletableFuture syncRq;
			if ((syncRq = syncPromiseMap.remove(inMsg.getMessageId())) != null) syncRq.complete(inMsg);
			return false;
		}
		if (logger.isDebugEnabled()) logger.debug("Receive headers {}", inMsg.headers);

		return true;
	}

	private boolean syncHandle(BiDMsg inMsg, CompletableFuture syncRq) {
		if (syncRq != null) {
			//Check sync request
			if (logger.isDebugEnabled())
				logger.debug("Complete sync request. id={}, msg={}", id, inMsg);
			if (inMsg.isPeerDrop()) syncRq.complete(null);
			else syncRq.complete(inMsg);
			return false;
		}
		return true;
	}

	private boolean timeoutCheckHandle(BiDMsg inMsg) {
		if (sendTimeoutMs > 0) { //enable timeout
//			Long sendTime = mapSentTime.remove(msg.messageId); //Check timeout
			long inSentTime = inMsg.getSentTime();
			if (inSentTime > 0L) { // popped from ack
				if (TimeUtils.isTimeoutMili(inSentTime, sendTimeoutMs)) { // timeout
//					TcpBasedHandler handler = getHandler();
//					if (handler != null) {
						if (logger.isDebugEnabled())
							logger.debug("[Timeout] Invoke timeOutHandler. id={},  latencyNs={}, timeoutMs={}",
								id, inMsg.sinceSentTime(), sendTimeoutMs);
						try {
							CompletableFuture.runAsync(() -> {
									// TODO LB async finish here
									try {
										handler.chassisTimeoutHandle(inMsg.getMessageId());
										inMsg.decompose(); // call in thread pool
									} catch (Exception ex) {
										logger.error(ex, ex);
									}
								},
								ChassisThreadManager.timeoutExecutor);
						} catch (Exception ex) {
							logger.error(ex, ex);
							inMsg.decompose();
						}
//					} else { // timeout but no handle
//						if (logger.isDebugEnabled())
//							logger.debug("[Timeout] No handler, ignore {latency={}ms}", TimeUnit.NANOSECONDS.toMillis(inMsg.sinceSentTime()));
//						inMsg.decompose();
//					}
					return false;
				}
			} else if (inSentTime == 0L && to3rdParty){
				return true; // allow serve all if not monitor for msgId
			} else {
				// else not found in map or not enable timeout
				if (logger.isDebugEnabled())
					logger.info("Timeout already triggered. ignore message. id={}, msgID={}", id, inMsg.getMessageId());
				inMsg.decompose();
				return false;
			}
		} // else disable timeout -> go to next handle
		return true;
	}
	@Override
	protected void decodeHandler(ChannelHandlerContext ctx, BiDMsg inMsg) {
		String msgId = inMsg.getMessageId();
		if(logger.isDebugEnabled()) logger.debug("Client received Msg {} id {} content length {} toStr {} hex {} and header {}", inMsg.getUrl(), inMsg.getMessageId(), inMsg.getContent().length, inMsg, inMsg.contentHexString(), inMsg.headers());
		if (logger.isInfoEnabled()) {
			inMsg.headers().set(inRqHeader, TimeUtils.nowNano() - inMsg.getDecodeTime());
		}
		mon.inClientReplyQueue(inMsg.getDecodeTime(), handlerPool.getQueueSize());
		inMsg.setSentTime(counter.get().ack(inMsg.isPing()
			? MsgType.PING.getUrl() // ping and create watch by "ping"
			: msgId, true));
		// client call url nao thi mac dinh reply ve url do, co the doi url neu can
		//drop van la 1 message va can xu ly chu khong return luon o client
		if (!(
//			dropCheck(tcpCtx) &&
				inMsg.isServerPush() // not count for LB
			? /* server push, no count */ asyncHandleOrsentDropHandle(wrapCtx(ctx, inMsg))  // server push flow, without drop by timeout not found in mapSendTime
			: /* request response, count if havent count */ (
			ChassisMgrableMsg.duplicateFilter(inMsg.getSentTime())
				&& clientMgrMsgHandle(inMsg) // internal msg without call to userhandler or return on syncSend
				/* not mgrMsg *//* sync */
				&& syncHandle(inMsg, syncPromiseMap.remove(msgId))  // first create msg is sendSync, but handle in clientMgrMsgHandle
				/* async */
				&& timeoutCheckHandle(inMsg) // timeout > drop > handle
				&& asyncHandleOrsentDropHandle(wrapCtx(ctx, inMsg)) // normal flow
		))) {

			// khong duoc return o day de tiep tuc doc cac msg khac trong buffer
		}
	}



}
