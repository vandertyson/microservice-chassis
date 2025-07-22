/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.client;

import com.viettel.vocs.common.MathUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.AnchorExecutor;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.handler.ClientHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.dns.ServiceMonitor;
import com.viettel.vocs.microchassis.connection.event.ClientEvent;
import com.viettel.vocs.microchassis.connection.event.ConnectionEvent;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.event.EventCatalog;
import com.viettel.vocs.microchassis.connection.exception.NoAvailableConnection;
import com.viettel.vocs.microchassis.connection.exception.NoConnectionForStrategyToSelect;
import com.viettel.vocs.microchassis.connection.exception.SendTimeout;
import com.viettel.vocs.microchassis.connection.exception.StrategyException;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.ManoConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.*;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelMatcher;
import io.netty.util.concurrent.Future;
import lombok.Getter;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.viettel.vocs.common.CommonConfig.RateLimiterConfig.DEFAULT_WINDOW;

public abstract class NettyClient<
	Req extends ChassisMgrableMsg,
	Res extends ChassisMgrableMsg,
	Conf extends ClientConfiguration,
	CHandler extends ClientHandler>
	extends MainSite<Req, Res, Conf, CHandler> {
	protected static final Logger logger = LogManager.getLogger(NettyClient.class);
	/**
	 * @author tiennn18
	 * <p> LB function
	 * this object equivalent to NettyClient/LoadBalancer-1:1-BusinessManager
	 * which control action over connections with clues read from strategy, for a specific serviceName (included blue green case)
	 * in update cases/patterns: this object manage routing in LB strategy, which hold more than 1 businessManager
	 * Strategy is self-run component that negotiate and update route rule
	 * Strategy then offer LB connectionId for sending by call any() or resolve(String key) method
	 * ConnectionWrapper (now is Connection abstract) manage TPS (include warmup-a special TPS control stage), concurrent on single connection *
	 * WarmupController controls global warm up states and orchestrates warmer stage of each ConnectionWrapper
	 * ConcurrentManager assist global control for TrafficLimiter(s) inside ConnectionWrapper(s)
	 * TrafficCounter count global statistics
	 * LBNegotiator negotiate configure globally with other services
	 */
	protected final Consumer<ConnectionEvent> siteEventHandler = param -> {
		long ccount = countConnected();
		logger.info("Connected count {}", ccount);
		ClientEvent spr = ccount == 0
			? new ClientEvent(ClientEvent.EventType.CONNECT_FAIL, this,
			String.format("Connection ID %s can not create connection to service %s", config.id, hostName))
			: new ClientEvent(ClientEvent.EventType.CONNECT_SUCCESS, this,
			String.format("Connection ID %s successfully create connection to %s", config.id, hostName));
		eventHandler.triggerEvent(spr);
	};
	@Getter
	protected final UpdateStrategy<Conf> updater;

	protected NettyClient(Conf clientConfig) {
		// Create new ServiceTemplate then use it to generate sites, then ref by NettyClient
		// update actions controlled under NettyClient
		super(Objects.requireNonNull(clientConfig, "Client configuration is required"));
		this.mainSite = this;
		// register site eventHandlers
		eventHandler.addEventHandler(ConnectionEvent.EventType.CONNECTION_UP, (Consumer<ConnectionEvent>) event -> {
			logger.info("[Connected to server] channel={}", event.getConnection().getChannel());
			siteEventHandler.accept(event);
		}).addEventHandler(ConnectionEvent.EventType.CONNECTION_DOWN, (Consumer<ConnectionEvent>) event -> {
			logger.info("[Disconnected to server]");
			siteEventHandler.accept(event);
		}).addEventHandler(ConnectionEvent.EventType.SERVICE_NAME_VERIFICATION_FAIL, (Consumer<ConnectionEvent>) event -> {
			logger.info(event.message);
			onServiceDown(event.getConnection());
		});
		updater = new RollingUpdateSiteStrategy<>(this);
		// sau super da co resolvedIPs
		logger.info(ReflectionToStringBuilder.toString(config, ToStringStyle.MULTI_LINE_STYLE, false));

		// add more handler for eventHandler, these handlers is for
		eventHandler
			.addEventHandler(ConnectionEvent.EventType.TIMEOUT_ISOLATION, siteEventHandler)
			.addEventHandler(ContextEvent.EventType.SERVER_DOWN, (Consumer<ContextEvent>) event -> {
				try {
					Channel channel = event.getChannel();
					logger.info("Receive server down signal. ctx={}", channel);
					updater.gracefulCloseAllRelevantConns(channel);
//                //Re update connection warm status

					// khong can readyAtLeast1Connection nua, lb.warmer da co monitor thread
				} catch (Exception ex) {
					logger.error(ex, ex);
				}
			});
	}

	public final void start(NettyClient<Req, Res, Conf, CHandler> oldClient) throws TimeoutException {
		start(oldClient.handler);
	}
//	public final void start(ClientHandler... handlers) throws TimeoutException {
//		List<String> errorString = new ArrayList<>();
//		ClientHandlers clientHandlers = ClientHandlers.newInstance();
//		for (ClientHandler handler : handlers) {
//			String version = ConnectionManager.checkAsyncHandlerSupportVersion(handler);
//			if (version == null)
//				errorString.add(String.format("Handler type %s passed to client %s is not supported", handler.getClass().getSimpleName(), config.id));
//			else clientHandlers.setProtoVersionHandler(version, handler);
//		}
//		if (!errorString.isEmpty()) throw new IllegalArgumentException(String.join("\n", errorString));
//		if (clientHandlers.size() > 0) start(clientHandlers);
//		// else skip
//	}

	/**
	 * init for multiple handler
	 * if not allow multiple, just init with first handler
	 *
	 * @param handler multi handler for versions supported
	 * @throws TimeoutException for waitStable at first start to not throw sent message into black hole
	 */
	public final void start(CHandler handler) throws TimeoutException {
		// for async assign handler
		if (!nettyInitialized.fuse(true)) {
			logger.error("Client {} is already initiated.", config.id);
			return;
		}
		long start = TimeUtils.nowNano();
		// LB init
		// self trigger press
		lbMonitor.execute(
			TimeUtils.sharedAEX.createInfinite(AnchorExecutor.TriggerPoint.Downside,
				() -> routeStrategyRef.get().pressAll(), // continuously refresh strategy params
				DEFAULT_WINDOW.get()));// 500_000_000L)); // run with LB-monitor so no need to reclassify at re/deregister
		// TODO check netty connect time of new connection to reduce this scan
		logger.info("[{}] LBmon-MS started", getId());
		lbMonitor.execute( // strategy need to ref to connections, so define LB without strategy and set later
			TimeUtils.sharedAEX.createInfinite(AnchorExecutor.TriggerPoint.Downside,
				() -> updater.getSites().values().parallelStream().forEach(Site::refresh), // continuously reclassify ready connections
				DEFAULT_WINDOW.get())); // combine LB-monitor scan with reclassify at re/deregister/new channel/isConencted check
		logger.info("[{}] LBmon-NC started", getId());

		this.handler = Objects.requireNonNull(handler, "Cannot set null CtxHandler for NettyClient");

		initDNS(); // chi duoc chay sau khi da assign handler case async
		logger.info("[Client initializing]{id={}, host={}, port={}, maxConnection={}}",
			config.id, config.host, config.port, mesh.maxConnection);

		// neu khong ket noi duoc o lan resolveDNS dau tien trong client.init, thi se wait o day den khi monitor job updateDNS
		try {
			TimeUtils.waitUntil(this::isConnected, config.channelConfiguration.timeoutConnectMs);
			logger.info(String.format("[Client:%s] Connected after %.2f ms", config.id, TimeUtils.miliPassed(start)));
		} catch (TimeoutException e) {
			String message = String.format("[Client:%s] Can not initialize connection to %s:%d after %.2fms",
				config.id, config.host, config.port, TimeUtils.miliPassed(start));
			logger.warn(message);
			ClientEvent clientEventParam = new ClientEvent(ClientEvent.EventType.CONNECT_FAIL, this, message);
			eventHandler.triggerEvent(clientEventParam);
		}
		ServiceMonitor.setMonitorServices(this);
		if (config.startWaitStable)
			waitStable(ServiceMonitor.serviceDiscoveryDurationNanos() + 2 * config.sendTimeoutMs); // = DNS cylce + 2 ping sent // should be less than 2 service discovery cycle
	}


	/**
	 * Cac ham send chi duoc dinh nghia va quan ly tai NettyClient, site cung cap connection
	 * -> cac bien sau co scope NettyClient, khong co o Site:
	 * 1. eventHandler
	 * 2. ctx
	 * 3. channel/nettychannel
	 */
	public void send(Req request, String... routeKeys) throws Exception {
		request.setEntrustedStamp();
		TimeUtils.NanoBeacon startBeacon = new TimeUtils.NanoBeacon(config.sendTimeoutMs); // sendTimeout count from when client call send
		checkInit();
		try {
			getActiveConnectionAndWait(startBeacon, routeKeys)
				.send(request); // set isSent in here
		} catch (Exception ex) {
			if (!(ex instanceof SendTimeout)) request.decompose(); // keep for retry
			throw ex;
		}
	}

	public Res sendSync(Req request, long timeoutMs, String... routeKeys) throws Exception {
		request.setEntrustedStamp();
		TimeUtils.NanoBeacon startBeacon = new TimeUtils.NanoBeacon(timeoutMs); // sendTimeout count from when client call sendSync here
		checkInit();
		try {
			return getActiveConnectionAndWait(startBeacon, routeKeys)
				.sendSync(request, timeoutMs); // set isSent in here
		} catch (Exception ex) {
			if (!(ex instanceof TimeoutException)) request.decompose(); // keep for retry
			throw ex;
		}
	}

	private Pair<Endpoint, Res> sendSync(Req request, long timeoutMs, ClientConnection<Req, Res, Conf, CHandler> routable) {
		checkInit();
		Res res = null;
		try {
			res = routable.sendSync(request, timeoutMs);
		} catch (Exception exception) {
			if (!(exception instanceof TimeoutException)) request.decompose(); // keep for retry
			logger.error("Broadcast message failed to " + routable.getEndpoint(), exception);
		}
		return Pair.of(routable.getEndpoint(), res);
	}

	private void send(Req request, ClientConnection<Req, Res, Conf, CHandler> routable) {
		checkInit();
		try {
			routable.send(request);
		} catch (Exception exception) {
			if (!(exception instanceof TimeoutException)) request.decompose(); // keep for retry
			logger.error("Broadcast message failed to " + routable.getEndpoint(), exception);
		}
	}

	public Res sendSync(Req request, String... routeKeys) throws Exception {
		return sendSync(request, config.sendTimeoutMs, routeKeys);
	}

	public void sendNoTimeout(Req request, String... routeKeys) throws Exception {
		request.setEntrustedStamp();
		request.setSentTime(0L); // disable timeout monitor
		send(request, routeKeys);
	}

	private void send(List<ClientConnection<Req, Res, Conf, CHandler>> targets, Req message, boolean broadcast) {
		if (broadcast) {
			try {
				makeBroadcastJobs(targets, message).entrySet().stream().parallel().forEach(job ->
					job.getValue().getValue().stream()
					.filter(ClientConnection::isConnected)
					.findAny() // only send once
					.ifPresent(conn -> send(job.getValue().getKey(), conn)));
			} finally {
				message.decompose();
			}
		} else {
			send(message, targets.get(MathUtils.randomNextInt(targets.size())));
		}
	}

	private Map<Endpoint, Res> sendSync(List<ClientConnection<Req, Res, Conf, CHandler>> targets, Req message, boolean broadcast, long totalTimeoutMs) {
		if (broadcast) {
			try {
				Map<String, Pair<Req, List<ClientConnection<Req, Res, Conf, CHandler>>>> mapJob = makeBroadcastJobs(targets, message);
				return mapJob.entrySet().stream().parallel()
					.map(job -> { // job distinct by IP so no rememberE dulicate -> when collect no duplicatedKeyException throw
						Endpoint rememberE = null;
						for (ClientConnection<Req, Res, Conf, CHandler> conn : job.getValue().getValue()) {
							rememberE = conn.endpointRepresent;
							Pair<Endpoint, Res> pair = sendSync(job.getValue().getKey(), totalTimeoutMs, conn);
							if (pair.getValue() != null) return pair;
						}
						return Pair.of(rememberE, (Res) null);
					}).filter(p -> p.getValue()!=null).collect(Collectors.toMap(Pair::getKey, Pair::getValue, (a, b) -> a, HashMap::new));
			} finally {
				message.decompose();
			}
		} else {
			for (ClientConnection<Req, Res, Conf, CHandler> conn : targets){
				Pair<Endpoint, Res> pair = sendSync(message, totalTimeoutMs, conn);
				return Map.of(conn.endpointRepresent, pair.getValue()); // khong can decompose, MsgEncoder will do
			}
			return new HashMap<>(); // khong co target nao
		}
	}

	private void send(List<ClientConnection<Req, Res, Conf, CHandler>> connected, Req message, boolean broadcastAllFound, boolean forceSend) {
		try {
			if (!connected.isEmpty()) {
				send(connected, message, broadcastAllFound);
			} else if (forceSend) {
				logger.error("No connected channel for matcher. Random push");
				send(message);
			}
		} catch (Exception ex) {
			logger.error(ex, ex);
		}
	}

	private Map<Endpoint, Res> sendSync(List<ClientConnection<Req, Res, Conf, CHandler>> connected, Req message, boolean broadcastAllFound, boolean forceSend, long totalTimeoutMs) {
		try {
			if (!connected.isEmpty()) {
				return sendSync(connected, message, broadcastAllFound, totalTimeoutMs);
			} else if (forceSend) {
				logger.error("No connected channel for matcher. Random push");
				return Map.of(endpoint, sendSync(message, totalTimeoutMs));
			}
			return new HashMap<>(); // rong nghia la khong gui di dau, khong co loi do gui dung config yeu cau roi
		} catch (Exception ex) {
			logger.error(ex, ex);
			return null; // de phan biet loi va khong loi
		}
	}

	public Map<Endpoint, Res> sendSync(Req request, ChannelMatcher matcher, boolean broadcastAllFound, boolean forceSend) {
		request.setEntrustedStamp();
		return sendSync(request, matcher, broadcastAllFound, forceSend, config.sendTimeoutMs);
	}

	public Map<Endpoint, Res> sendSync(Req request, ChannelMatcher matcher, boolean broadcastAllFound, boolean forceSend, long timeoutMs) {
		request.setEntrustedStamp();
		return sendSync(routeStrategyRef.get().getDestMap().values().stream()
			.filter(conn -> matcher.matches(conn.getChannel()))
			.map(r -> (ClientConnection<Req, Res, Conf, CHandler>) r)
			.collect(Collectors.toList()), request, broadcastAllFound, forceSend, timeoutMs);
	}

	public void send(Req request, ChannelMatcher matcher, boolean broadcastAllFound, boolean forceSend) {
		request.setEntrustedStamp();
		send(routeStrategyRef.get().getDestMap().values().stream()
			.filter(conn -> matcher.matches(conn.getChannel()))
			.map(r -> (ClientConnection<Req, Res, Conf, CHandler>) r)
			.collect(Collectors.toList()), request, broadcastAllFound, forceSend);
	}

	public Map<Endpoint, Res> sendSync(Req message, Pattern destIpOrDnsFilter, boolean broadcastAllFound, boolean forceSend) {
		return sendSync(message, destIpOrDnsFilter, broadcastAllFound, forceSend, config.sendTimeoutMs);
	}

	public Map<Endpoint, Res> sendSync(Req request, Pattern destIpOrDnsFilter, boolean broadcastAllFound, boolean forceSend, long timeoutMs) {
		request.setEntrustedStamp();
		return sendSync(routeStrategyRef.get().getDestMap().values().stream()
			.filter(conn -> conn.getEndpoint().ip != null && destIpOrDnsFilter.matcher(conn.getEndpoint().ip).matches()
				|| conn.getEndpoint().serviceName != null && destIpOrDnsFilter.matcher(conn.getEndpoint().serviceName).matches())
			.map(r -> (ClientConnection<Req, Res, Conf, CHandler>) r)
			.collect(Collectors.toList()), request, broadcastAllFound, forceSend, timeoutMs);
	}

	public void send(Req request, Pattern destIpOrDnsFilter, boolean broadcastAllFound, boolean forceSend) {
		request.setEntrustedStamp();
		send(routeStrategyRef.get().getDestMap().values().stream()
			.filter(conn -> conn.getEndpoint().ip != null && destIpOrDnsFilter.matcher(conn.getEndpoint().ip).matches()
				|| conn.getEndpoint().serviceName != null && destIpOrDnsFilter.matcher(conn.getEndpoint().serviceName).matches())
			.map(r -> (ClientConnection<Req, Res, Conf, CHandler>) r)
			.collect(Collectors.toList()), request, broadcastAllFound, forceSend);
	}

	public Map<Endpoint, Res> sendSyncBroadcast(Req message) {
		return sendSyncBroadcast(message, config.sendTimeoutMs);
	}

	public void sendBroadcast(Req request) {
		request.setEntrustedStamp();
		send(routeStrategyRef.get().getDestMap().values().stream().map(r -> (ClientConnection<Req, Res, Conf, CHandler>) r).filter(Objects::nonNull).collect(Collectors.toList()), request, true, true);
	}

	public Map<Endpoint, Res> sendSyncBroadcast(Req request, long timeoutMs) {
		request.setEntrustedStamp();
		return sendSync(routeStrategyRef.get().getDestMap().values().stream().map(r -> (ClientConnection<Req, Res, Conf, CHandler>) r).filter(Objects::nonNull).collect(Collectors.toList()), request, true, true, timeoutMs);
	}

	public Map<Endpoint, Res> sendSync(Req message, ChannelMatcher destIpOrDnsFilter) {
		return sendSync(message, destIpOrDnsFilter, true, false);
	}

	public void send(Req message, ChannelMatcher destIpOrDnsFilter) {
		send(message, destIpOrDnsFilter, true, false);
	}

	public Map<Endpoint, Res> sendSync(Req message, Pattern destIpOrDnsFilter) {
		return sendSync(message, destIpOrDnsFilter, true, false);
	}

	public void send(Req message, Pattern destIpOrDnsFilter) {
		send(message, destIpOrDnsFilter, true, false);
	}

	public boolean checkAndWaitStableAllSite(long durationToExitNs) {
		return updater.getSites().values().stream().allMatch(s -> s.checkAndWaitStable(durationToExitNs));
	}

	public void waitStable(long timeoutNano) throws TimeoutException {
		long start = TimeUtils.nowNano();
		logger.warn("[Client:{}] Wait for stabling", config.id);
		if (!updater.getSites().values().stream().allMatch(s -> {
			boolean isStable = s.checkAndWaitStable(timeoutNano);
			if (!isStable)
				logger.warn("Site {} not stable after {}ms/{}ms", s.getEndpoint(), TimeUtils.miliPassed(start), TimeUnit.NANOSECONDS.toMillis(timeoutNano));
			return isStable;
		})) {
			throw new TimeoutException(String.format("Client %s to %s not stable after %.2fms", config.id, endpoint, TimeUtils.miliPassed(start)));
		}
	}

	public void logConnectionStat() {
		// checkInit(); da check init trong ham goi ham nay
		if (logger.isInfoEnabled()) {
			logger.info(updater.getConnectionStatString());
			logger.info(routeStrategyRef.get().report());
		}
		if (logger.isDebugEnabled())
			logger.debug(updater.getConnectionDetailString());
	}

	@Override
	public boolean isConnected() {
		return updater.isEnable()
			? updater.isConnected() // dang enable update, check it nhat 1 connection den bat ky host nao trong nghiep vu
			: routeStrategyConnected(); // neu dang khong chia cho update client khac, thi check connected cua nettyclient nay
	}

	private ClientConnection<Req, Res, Conf, CHandler> getConnection(TimeUtils.NanoBeacon startBeacon, String key) throws StrategyException, SendTimeout {
		// checkInit(); da check init trong ham goi ham nay
		/**
		 * Viec get connection giup kiem soat gui toi cac site cua qua trinh update theo updateStrategy
		 * 	chung quy viec gui den cac host chi khac nhau o nettychannel, nettyclient van chung cho 1 baseHostname (tat ca cac site giong nhau)
		 * not safe neu khong kiem soat boi bat ky routeStrategy nao,
		 * 	default cung phai la RoundRobin+StandaloneLB+FrontPressure(TPS=0)
		 * 	neu de application goi getConnection va su dung object Connection<> se khien cac bo dem hoat dong sai
		 * 	 TODO danh gia tac dong cua viec cap Connection interface cho application gui free
		 */
		try {
			return (ClientConnection<Req, Res, Conf, CHandler>) (key != null
				? updater.select(startBeacon, key) // By default, request key is the last param
				/**
				 * Kiem soat gui qua updateStrategy (multi Site) hay routeStrategy (single Site)
				 *
				 * function nay da bao gom warmUp
				 * 				 warmup TPS da duoc kiem soat o ConnectionWrapper.limiter.mode, chi viec gui vao la duoc
				 * 				 khong can draw o day
				 * 				 -> se lam notReadyRater > real warmup speed
				 * 				 -> lam cho cac connection nao duoc warmup bang select key se end warmMode nhanh hon
				 * 				 	-> sau do notReadyRate cap nhat TPS lai, cac connection checkMode
				 * 				  -> warmAny se khong draw duoc tren cac select key connection -> tang toc end cho cac connection khong select key
				 * 				 	-> neu connection duoc chon dang warmup ma select nhieu thi se lam cham lai cac request sau do.
				 * 				  => khong co anh huong, cac yeu to tang toc do deu duoc rater trong tung ConnectionWrapper kiem soat
				 * 				 TODO check draw limiter o day
				 */
				: updater.select(startBeacon));
		} catch (NoConnectionForStrategyToSelect ignored) {
			return null;
		}
	}

	private ClientConnection<Req, Res, Conf, CHandler> getActiveConnectionAndWait(TimeUtils.NanoBeacon startBeacon, String... routeKey) throws Exception {
		long start = TimeUtils.nowNano();
		do {
			try {
				long aSelectStart = TimeUtils.nowNano();
				ClientConnection<Req, Res, Conf, CHandler> conn = getConnection(startBeacon, routeKey != null && routeKey.length > 0 ? routeKey[0] : null);
				mon.strategySelect(aSelectStart);
				if (conn != null && conn.isConnected()) {
					mon.strategy(start, config.routeStrategy.logId); // "StratSel"
					if (logger.isDebugEnabled())
						logger.debug("Selected connId=" + conn.getId() + " channel=" + conn.getChannelString());
					return conn;
				} else {
					if (logger.isDebugEnabled())
						logger.debug("Temporary no active connection on client " + getId());
				}
			} catch (StrategyException ignored) { // error on 1 select
			}
		} while (
			!TimeUtils.isTimeoutMili(start, config.channelConfiguration.timeoutConnectMs) &&
				!startBeacon.isTimeout() &&
				TimeUtils.waitSafeNano(config.channelConfiguration.getConnectionWaitNano));
		// cannot choose after retry and wait
		if (logger.isDebugEnabled()) logConnectionStat();
		eventHandler.triggerEvent(new ClientEvent(ClientEvent.EventType.CONNECT_FAIL, this, String.format("[%s:%s] Client can not get an active connection after %sms",
			config.id, endpoint, TimeUtils.miliPassed(start))));
		throw new NoAvailableConnection(routeStrategyRef.get(), String.format("No connection available for client id %s after %sms", config.id, TimeUtils.miliPassed(start)));
	}

	@Override
	public String getUpdateHostName() {
		if (updater instanceof BlueGreenUpdateSiteStrategy) {
			return BlueGreenUpdateSiteStrategy.createPairHostname(hostName);
		} else return updater.otherSiteNames(hostName).get(0);
	}

	public void close() {
		updater.getSites().values().forEach(Site::siteClose);
		updater.disable(true);
		Future<?> shutdownGracefully = workerGroup.shutdownGracefully();
		try {
			shutdownGracefully.get(30, TimeUnit.SECONDS);
		} catch (Exception e) {
			logger.error(e, e);
		}
		try {
			eventHandler.close();
		} catch (Exception e) {
			logger.error(e, e);
		}
		try {
			lbMonitor.shutdownNow();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}


	public boolean isResolved(EventCatalog event, String serviceName) {
		Site<Conf> site = updater.getSite(serviceName);
		if (site != null) {
			int eventCode = event.getCode();
			if (eventCode == ClientEvent.EventType.CONNECT_FAIL.getCode())
				return site.isConnected();
			if (eventCode == ClientEvent.EventType.DISCOVERY_FAIL.getCode())
				return site.nResolved() > 0;
			if (eventCode == ConnectionEvent.EventType.CONNECTION_DOWN.getCode()
				|| eventCode == ConnectionEvent.EventType.CONNECTION_INIT_FAIL.getCode()
				|| eventCode == ConnectionEvent.EventType.CONNECTION_WRITE_FAIL.getCode())
				return site.isConnected();
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("NettyClient{id=%s, type= %s, host=%s, port=%d}", config.id, type, hostName, config.port);
	}

	public void disableUpdateControl() {
		updater.disable(false);
	}

	public ManoConfigure createManoConfigAndEnableManoControl() {
		return BlueGreenUpdateSiteStrategy.createPairHostname(hostName) != null // legit
			? updater.enable(this) // create sites
			: null; // not legit (ipv4 direct client) -> skip mano BG update
	}

	public ClientStatus getClientStatus() {
		return updater.getStatuses(new ClientStatus(config.id));
	}
}
