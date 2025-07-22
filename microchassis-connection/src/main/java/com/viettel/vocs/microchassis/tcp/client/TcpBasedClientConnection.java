package com.viettel.vocs.microchassis.tcp.client;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpBasedHandler;
import com.viettel.vocs.microchassis.connection.RequestTimeoutMonitor;
import com.viettel.vocs.microchassis.connection.client.ChannelAttribute;
import com.viettel.vocs.microchassis.connection.client.ClientChannelAttribute;
import com.viettel.vocs.microchassis.connection.client.ClientConnection;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.ConnectionEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.exception.ConnectionException;
import com.viettel.vocs.microchassis.connection.exception.SendTimeout;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ClientLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tcp.codec.MsgHeader;
import com.viettel.vocs.microchassis.util.MonitorManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public abstract class TcpBasedClientConnection<BiDMsg extends Msg, Conf extends ClientConfiguration, CHandler extends TcpBasedHandler>
	extends ClientConnection<BiDMsg, BiDMsg, Conf, CHandler> {
	private static final Logger logger = LogManager.getLogger(TcpClientConnection.class);

	protected TcpBasedClientConnection(Conf config, EventHandler eventHandler, Site<Conf> ownerSite, Endpoint endpoint, CHandler handlers, Bootstrap bootstrap, AtomicReference<RouteStrategy> routeStrategyRef) {
		super(config, ownerSite, eventHandler, endpoint, handlers, bootstrap, routeStrategyRef);
		logger.info("[{}] Creating TCP connection. clientId={}", endpointRepresent, config.id);
		init();
	}

	@Override
	public void onChannelRenew(Channel newChannel) {
		this.setClientChannel(newChannel);
		setEnable(true);
		super.onChannelRenew(newChannel);
	}



	@Override
	public void onChannelReady(Channel channel, long startConnect) throws Exception {
		String sServerConn;
		// server will allow any connection, but for connection mute, else if speak anything, need first packet is MsgType.CREATE, else server will drop
		boolean pong = false;
		int count = 0;
		do {
			try {
				count++;
				BiDMsg res;
				try {
					BiDMsg request = makeCreateMsg(channel.alloc());
					Map<String, CompletableFuture> syncPromiseMap;
					while ((syncPromiseMap = TcpClientChannelAttribute.getPromiseMap(channel)) == null)
						TimeUtils.waitSafeMili(1);

					logger.info("[{}] Established, WAIT FOR FIRST msgId={} on channel {} with map {} @ {}", endpointRepresent, request.getMessageId(), channel, syncPromiseMap, System.identityHashCode(syncPromiseMap));
					try {
						res = sendSyncByChannel(channel, request, config.sendTimeoutMs);
					} finally {
						request.decompose();
					}
					logger.info("[{}] Receive CREATE res={}", endpointRepresent, res);
					if (res == null) {
						if (channel.isActive()) {
							logger.error("[{}] Ask connection failed, res null, retry.", endpointRepresent);
							continue; // ask failed, retry if connection still open
						} else {
							throw new ConnectionException(String.format("Server %s response first create with empty msg", endpointRepresent), this,
								ConnectionException.CONN_ERROR.INIT_CONNECT); // stop try creating
						}
					} else { // verify connection
						logger.info("[{}] FIRST res {} header={}", endpointRepresent, res.getMessageId(), res.getMapHeaders());
						verifyRemoteHostname(res); // verify if this endpoint have allocated for other pod // this throw ConnectionException
					}
				} catch (TimeoutException | SendTimeout e) {
					// timeout retry
					if (channel.isActive()) { // connection still open
						logger.error("[{}] Timeout send create request to TCP channel (id:{}) after {}ms. TCP connection initialized failed but created. Close channel then recreate later.", endpointRepresent, config.id, TimeUtils.miliPassed(startConnect));
						channel.close();
					}
					// closed by server for many reason: serverStop, network problem, deny by max allow, .. -> remove this connection
					deregister();
					logger.error(e, e);
					throw new ConnectionException("[" + endpointRepresent + "] Server refused connection", this,
						ConnectionException.CONN_ERROR.REFUSE_CONNECT,
						ConnectionException.CONN_ERROR.INIT_CONNECT);   // unknown reason
				}
				lastPing = System.currentTimeMillis();
				// from here res != null and has header comes from valid host
				pong = true;
				switch (channel.attr(ClientChannelAttribute.mainSiteRef).get().getMesh().mode) {
					case CENTER_LIMIT:
					case FULL_MESH:
						// accept all response from server, pong.
						break;
					case ASK_LIMIT:
					default:
						logger.info("[{}] Second Header {}", endpointRepresent, res.headers());
						if ((sServerConn = res.headers().popString(MsgHeader.MESH_HEADER)) != null) {
							try {
								nServerConn = Integer.parseInt(sServerConn);
							} catch (NumberFormatException e) {
								throw new ConnectionException("Server " + endpointRepresent + " response first create with invalid MESH_HEADER", this,
									ConnectionException.CONN_ERROR.INIT_CONNECT); // stop try creating
							}
							if (nServerConn < 0) { // not allow to create (politely) from server suggested
								deregister(); // self close // Limit server limit is guaranteed here, full mesh is the same with super large server limit :))
								throw new ConnectionException("Server " + endpointRepresent + " refused connection, max allowed", this,
									ConnectionException.CONN_ERROR.REFUSE_CONNECT,
									ConnectionException.CONN_ERROR.INIT_CONNECT);   // max conn
							}
							// break; // success -> return // no need break, set pong = true is ok
						} else {
							throw new ConnectionException("Server " + endpointRepresent + " response first create with empty MESH_HEADER", this,
								ConnectionException.CONN_ERROR.INIT_CONNECT); // stop try creating
						}
						break;
				}
				res.decompose();
			} catch (Exception ex) {
				channel.close();
				logger.error(ex, ex); // wrap minor error to retry
				if (ex instanceof ConnectionException) throw (ConnectionException) ex;
			}
		} while (!pong && count < config.maxRetryConnect);
	}

	@Override
	public String toString() {
		return "TcpConnection{"
			+ "id=" + config.id
			+ ", endpoint=" + endpointRepresent
			+ '}';
	}

	private void loadHeaders(BiDMsg request) {
		if (CommonConfig.InstanceInfo.VDU_NAME.get() != null)
			request.headers().putIfAbsent(MsgHeader.VDU_NAME, CommonConfig.InstanceInfo.VDU_NAME.get());
	}
	private static boolean isViolateRemoteHostname(String requiredName, String remoteName) {
		return requiredName != null && !requiredName.isEmpty()
			&& !"localhost".equals(requiredName) // this will make remove service has other true service name or null, then will be close
			&& !( /** client call abm.ns1, server response abm.ns1.vim1
		 * => match all
		 * => match all sub octet from beginning
		 */ remoteName.equals(requiredName) // => match all
			|| remoteName.startsWith(requiredName + ".") // => match all sub octet from beginning ( abm.ns1.vim1 contains abm.ns1. )
		);
	}
	private void verifyRemoteHostname(BiDMsg msg) throws ConnectionException {
		// vẫn giữ lại flow check verifyRemoteHostname du ben K8s da dam bao ko bi rotate IP
		String remoteService = msg.headers().getString(MsgHeader.SERVICE_NAME);
		String provide = null;
		if (remoteService != null // khac null moi check, neu null thi la ip connection hoac old version chassis, keep
			&& !(provide = "localhost").equals(remoteService) // accept remote is localhost bare listen all interface
			&& !(provide = "*").equals(remoteService) // accept all => keep
			&& isViolateRemoteHostname((provide = endpointRepresent.serviceName), remoteService)
			&& isViolateRemoteHostname((provide = config.authenServiceName), remoteService)
		) {
			String message = String.format("[%s@@%s][%s/%s] Failed to verify remote host name. Forcibly close connection. Provide=%s | Remote_service_name=%s", config.id, nettyChannel, endpointRepresent, config.authenServiceName, provide, remoteService);
//			ConnectionEventParam connectionEventParam = ;
//			connectionEventParam.getParams().put("ip", endpointRepresent.ip);
//			connectionEventParam.getParams().put("local_service_name", endpointRepresent.serviceName);
//			connectionEventParam.getParams().put("remote_service_name", remoteService);
			eventHandler.triggerEvent(new ConnectionEvent(ConnectionEvent.EventType.SERVICE_NAME_VERIFICATION_FAIL, this, message));
			msg.decompose();
			logger.error(message);
			deregister();
			throw new ConnectionException(message, this,
				ConnectionException.CONN_ERROR.REFUSE_CONNECT,
				ConnectionException.CONN_ERROR.CHANNEL_ERROR);
		} else logger.info(String.format("[%s@@%s][%s/%s] Verify host name successfully. Channel accepted. Provide=%s | Remote_service_name=%s", config.id, nettyChannel, endpointRepresent, config.authenServiceName, provide, remoteService));
	}

	@Override
	public boolean ping(ByteBufAllocator allocator) {
		BiDMsg res;
		long startPing = TimeUtils.nowNano();
		try {
			BiDMsg request = makePingMsg(allocator);
			try {
				res = sendSync(request, config.pingTimeoutMs);
			} finally {
				request.decompose();
			}
			if (res != null) {
				if (logger.isDebugEnabled()) {
					logger.info("[{}] Receive pong message on tcp channel. id={}, channel={}, payload={}, latencyMs={}",
						endpointRepresent, config.id, nettyChannel, res, TimeUtils.miliPassed(startPing));
				}
				verifyRemoteHostname(res);
				res.decompose();
				lastPing = System.currentTimeMillis();
				return true;
			}
		} catch (Exception ex) {
			logger.error("[{}] Can not get ping response after {} ms. exception {}", endpointRepresent, TimeUtils.miliPassed(startPing), ex.getMessage(), ex);
			ex.printStackTrace();
		}
		logger.error("[{}] Can not get ping response after {} ms", endpointRepresent, TimeUtils.miliPassed(startPing));
		return false;
	}


	protected abstract BiDMsg makeCreateMsg(ByteBufAllocator allocator);

	protected abstract BiDMsg makePingMsg(ByteBufAllocator allocator);

	private MonitorManager mon = MonitorManager.getInstance();


	@Override
	public ConnectionMode checkMode() {
		return overwriteMode(super.checkMode());
	}

	@Override
	public ConnectionMode skipWarmUp() {
		return overwriteMode(super.skipWarmUp());
	}


	@Override
	protected void sendByChannel(Channel channel, BiDMsg request, long timeoutMs, GenericFutureListener<ChannelFuture>... listeners) throws ConnectionException {
		if (channel != null) {
			presend(channel, request);
			String messageId = request.getMessageId();
			PeerCounter counter = ChannelAttribute.getCounter(channel);
			ChannelPromise newPromise = channel.newPromise()
				.addListener(f -> {
					mon.forwardTime(request.getDecodeTime());
					if (counter != null) counter.getState().stack();
				}); // first, stack to counter after flush, then resolve other promises
			if (listeners != null) newPromise.addListeners(listeners);
			/** BEFORE THIS LINE, ERRORS BELONG TO dropHandle (self drop),
			 * 		can retry, throw as exception
			 *  AFTER THIS LINE, ERRORS BELONG TO sentDropHandle (peer drop)
			 *  	do not retry, no throw, only handle in userHandler.sentDropHandle(msg)
			 */
			try { // neu ko co timeout, channel co the bi kill neu can thu hoi connection vuot qua so luong client.maxConnection, gay ra exception duoi day
				if (logger.isDebugEnabled()) logger.debug("Before flush sending = {}", request.report());
				request.headers().set("lastFlush", String.valueOf(TimeUtils.nowNano())); // for later get
				watch(channel, request, timeoutMs);
				long startFlush = TimeUtils.nowNano();
				channel.writeAndFlush(request, newPromise.addListener(f -> MonitorManager.getInstance().clientFlush(startFlush, config.routeStrategy.logId))); // request decompose included in here
				if (writeException.get() && writeException.compareAndExchange(true, false))
					eventHandler.triggerEvent(new ConnectionEvent(ConnectionEvent.EventType.CONNECTION_WRITE_SUCCESS, this));
			} catch (ChannelException cle) {
				if (counter != null) counter.ack(messageId, false); // counter may be null if channel die/close
				logger.error("[{}] Connection closed while sending Msg", endpointRepresent, cle);
				getHandler().chassisDropHandle(request, true);
			}
		} else throw new NullPointerException(String.format("Connection %s send on null channel", getMonitorID()));
	}

	@Override
	public BiDMsg sendSync(BiDMsg request, long timeoutMs) throws Exception {
		try {
			loadHeaders(request);
			return sendSyncByChannel(nettyChannel, request, timeoutMs); // decompose by encoder
		} catch (SendTimeout timeoutE) {
			getHandler().chassisDropHandle(request, false); // at client there is no ctx
			throw timeoutE;
		} catch (Throwable internalError) {
			getHandler().chassisDropHandle(request, true); // at client there is no ctx
			throw internalError;
		}
	}

	@Override
	public void send(BiDMsg request) throws Exception {
		long start = TimeUtils.nowNano();
		try {
			loadHeaders(request);
			sendByChannel(nettyChannel, request, config.sendTimeoutMs); // decompose by encoder
		} catch (SendTimeout timeoutE) {
			getHandler().chassisDropHandle(request, false); // at client there is no ctx
			throw timeoutE;
		} catch (Throwable internalError) {
			getHandler().chassisDropHandle(request, true); // at client there is no ctx
			throw internalError;
		}
		mon.clientSend(start); // "AsyncSend"
	}

	private BiDMsg sendSyncByChannel(Channel channel, BiDMsg request, long timeout) throws Exception {
		if (channel != null) {
			Objects.requireNonNull(request.getMessageId(), "Request has no message ID. Can not send");
			CompletableFuture<BiDMsg> success = new CompletableFuture<>();
			Map<String, CompletableFuture> syncPromiseMap = TcpClientChannelAttribute.getPromiseMap(channel);
			syncPromiseMap.put(request.getMessageId(), success);
			try {
				sendByChannel(channel, request, timeout);
				if (timeout > 0) {
					return success.get(timeout, TimeUnit.MILLISECONDS);
				} else {
					return success.get(config.sendTimeoutMs, TimeUnit.MILLISECONDS);
				}
			} catch (SendTimeout |
							 TimeoutException e) { // SendTimeout is timeout at flush, TimeoutException is timeout at wait response
				if (logger.isDebugEnabled())
					logger.debug("ProcessTimeout from sendSyncByChannel for reqId={}", request.getMessageId());
				processTimeoutMsgId(channel, request.getMessageId());
				throw e;
			} finally {
				syncPromiseMap.remove(request.getMessageId());
			}
		} else
			throw new NullPointerException(String.format("Connection %s send on null channel", getMonitorID()));
	}

	@Override
	public void processTimeoutMsgId(Channel sendChannel, String msgId) {
		super.processTimeoutMsgId(sendChannel, msgId);
		isolateCheck();
	}

	public void isolateCheck() {
		ClientLimiter limiter = this.getLimiter();
		if (limiter != null && limiter.isolationEngine != null)
			limiter.isolationEngine.isIsolateCheck(this);
	}

	@Override
	public synchronized void close() {
		super.close(); // eliminate from management components
		if (nettyChannel != null) {
			try {
				RequestTimeoutMonitor.unregister(nettyChannel);
				nettyChannel.close();
			} catch (Exception e) {
				logger.error(e, e);
			}
		}
	}

}

