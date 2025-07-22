package com.viettel.vocs.microchassis.connection.loadbalancing.strategy;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.exception.RouteStrategyException;
import com.viettel.vocs.microchassis.connection.exception.ServiceNotAvailable;
import com.viettel.vocs.microchassis.connection.exception.StrategyException;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.FrontConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ModeRater;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.FrontPressureConnectionMode;
import com.viettel.vocs.microchassis.tcp.client.TcpBasedClientConnection;
import com.viettel.vocs.microchassis.tcp.client.TcpClientConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * RouteStrategy is a stateless manager, manages all action that span over connections,
 * for single hostname
 * stateless mean it static, calculate output value each time it is called
 * value updates are performed by LoadBalancer instance include negotiate cross horizontal instances (same hostname)
 * Destination is type or feedback resource
 * for RouteStrategy -> ConnectionWrapper<X,Y,Z>
 * for UpdateStrategy -> Site<X,Y,Z>
 */
public abstract class RouteStrategy
	extends Strategy<Routable> {
	protected static Logger logger = LogManager.getLogger(RouteStrategy.class);
	private final PeerConfig config;
	private final Map<String, Routable> connectionsRef; // for refs and classify only, do not read write
	protected final Map<String, Routable> warming = new ConcurrentHashMap<>();
	//	protected final Map<String, Routable> readiers = new ConcurrentHashMap<>();   //destMap act as readiers
	protected final Map<String, Routable> notReadiers = new ConcurrentHashMap<>();

	public abstract boolean isPressure();

	public int countReady() {
		return destMap.size();
	}

	public long countNotReady() {
		return notReadiers.size();
	}

	public long countWarmingUp() {
		return warming.size();
	}
	public final ModeRater notReadyRater = new ModeRater(
		new FrontPressureConnectionMode(new FrontConfigure(1)));


	public boolean hasWarming() {
		return warming.size() > 0;
	}

	protected String getWarmest() {
		if (hasWarming()) {
			return warming.entrySet().stream()
				.max(Comparator.comparingInt(entry -> entry.getValue().warmingUpTPS()))
				.map(Map.Entry::getKey)
				.orElse(null);
		}
		return null;
	}

	public void isolationCheck() {
		connectionsRef.values().stream()
			.filter(conn -> conn instanceof TcpClientConnection)
			.map(conn -> (TcpClientConnection) conn)
			.forEach(TcpBasedClientConnection::isolateCheck);
	}

	public void checkReclassify() {
		if (connectionsRef.values().stream().anyMatch(conn -> conn.warmingUpTPS() > 0) // has warming need to continuously check
			|| destMap.values().stream().anyMatch(conn -> !conn.isConnected() || !conn.checkUsable()) // has change in readiers
			|| notReadiers.values().stream().anyMatch(conn -> conn.isConnected() && conn.checkUsable()) // has change in notReadiers
			|| destMap.size() + notReadiers.size() + warming.size() != connectionsRef.size()
		) reclassifyAll();
	}

	private CONNECTION_STATUS markAsReady(Routable conn) {
		String connId = conn.getId();
		if (logger.isDebugEnabled()) logger.info("Classify {} as ready", connId);
		notReadiers.remove(connId);
		warming.remove(connId);
		addReadier(conn);
		conn.getState().set(CONNECTION_STATUS.READY);
		refresh();
		return CONNECTION_STATUS.READY;
	}

	private void addReadier(Routable conn) {
		String connId = conn.getId(); // re-get connId to be legit
		if (destMap.get(connId) == null) {
			destMap.put(connId, conn);
			afterDestAdded(conn);
		}
	}

	private CONNECTION_STATUS markAsWarming(Routable conn) {
		String connId = conn.getId();
		if (logger.isDebugEnabled()) logger.info("Classify {} as warming", connId);
		removeReadier(connId);
		notReadiers.remove(connId);
		warming.putIfAbsent(connId, conn);
		conn.getState().set(CONNECTION_STATUS.WARMING);
		refresh();
		return CONNECTION_STATUS.WARMING;
	}

	private void removeReadier(String connId) {
//		pendingRemove.add(connId);
//		Routable toRemove = destMap.remove(connId);
//		if (toRemove != null) {
//			pendingRemove.remove(connId);
//			rebuildCacheSortList();
//		}
		destMap.remove(connId);
		rebuildCacheSortList();
	}
	protected List<String> pendingRemove = new CopyOnWriteArrayList<>();



	protected void afterDestAdded(Routable newAdded) {
		rebuildCacheSortList();
	}

	public CONNECTION_STATUS markAsNotReady(Routable conn) {
		// null channel, not isConnected, not checkUsable
		String connId = conn.getId();
		if (logger.isDebugEnabled()) logger.info("Classify {} as notReady", connId);
		removeReadier(connId);
		warming.remove(connId);
		notReadiers.putIfAbsent(connId, conn);
		conn.getState().set(CONNECTION_STATUS.NOT_READY);
		refresh();
		return CONNECTION_STATUS.NOT_READY;
	}

	public void removeConn(String connId) {
		if (logger.isDebugEnabled()) logger.info("Remove conn {} from RouteStrategy", connId);
		removeReadier(connId);
		warming.remove(connId);
		notReadiers.remove(connId);
		refresh();
	}

	public void classifyConnection(Routable conn) { // classify in unknown strict conditions
		conn.checkMode();
		conn.isConnected(); // just need to call isConnected then it will trigger classify, kinda event-driven :D, this extremely reduce update of LBmonitor polling
		refresh();
	}
	public enum CONNECTION_STATUS {
		READY,
		WARMING,
		NOT_READY

	}

	public CONNECTION_STATUS classify(Routable conn) { // only classify for connected conn
		int warmTPS = conn.warmingUpTPS(); // warmingUpTPS() call to channel.attr(limiter), is the final step of routeStrategy apply, rely on this limiter to classify active channel
		if (warmTPS == 0) {
			return markAsReady(conn);
		} else if (warmTPS > 0) {
			return markAsWarming(conn);
		} else {
			return markAsNotReady(conn);
		}
	}

	public CONNECTION_STATUS dryClassify(boolean newConnectedState, Routable conn) {
		/**
		 * this equal old RouteStrategy.classify
		 */
		if (newConnectedState) { // turn to connected
			int warmTPS = conn.warmingUpTPS(); // warmingUpTPS() call to channel.attr(limiter), is the final step of routeStrategy apply, rely on this limiter to classify active channel
			if (warmTPS == 0) {
				return CONNECTION_STATUS.READY; // ready
			} else if (warmTPS > 0) {
				return CONNECTION_STATUS.WARMING;
			} else return CONNECTION_STATUS.NOT_READY;
		} else return CONNECTION_STATUS.NOT_READY; // turn to not connected
	}

	public void reclassifyAll() {
		/**
		 * every action affect to warmers item number need reclassify for other usage
		 * every action changes warmer from warm to ready or vice versa, need reclassify
		 */

		// first remove all closed connections
		Set<String> activeConns = new HashSet<>(connectionsRef.keySet());
		Set<String> closedIds = destMap.keySet().stream().filter(connId -> !activeConns.contains(connId)).collect(Collectors.toSet());
		closedIds.addAll(notReadiers.keySet().stream().filter(connId -> !activeConns.contains(connId)).collect(Collectors.toList()));
		closedIds.addAll(warming.keySet().stream().filter(connId -> !activeConns.contains(connId)).collect(Collectors.toList()));
		closedIds.forEach(this::removeConn);
		// then reclassify all remain connection
		connectionsRef.forEach((key, conn) -> {
			conn.checkMode();
			classifyConnection(conn);
		});
		// finally check warmup flow
		if (routeConfig.enableWarmup) {
			// always make first connection to host target to skip warm up, other notReadiers do warm up as usual
			if (destMap.isEmpty()) {
				String warmestIdx = getWarmest();
				if (warmestIdx != null && !warmestIdx.isEmpty())
					connectionsRef.get(warmestIdx).skipWarmUp();
			}
			/**
			 * sum up total TPS of all warming up for strategy draw for warm flow, this can re-setTPS at any time
			 */
			notReadyRater.getMode().getConfig().changeTPS(warming.values().stream().mapToInt(Routable::warmingUpTPS).sum());
		}
		refresh();
	}

	public abstract ConnectionMode newReadyMode(String channelId);

	/**
	 * @return null safe
	 * @throws RouteStrategyException
	 */
	public boolean isConnected(){
		return countReady() > 0;
	}
	public final List<Routable> anyWithWarmPrior(TimeUtils.NanoBeacon startBeacon) throws RouteStrategyException {
		if (!isConnected()) throw new ServiceNotAvailable(this, String.format("Client ID %s not connected for [%s:%d]", config.id, config instanceof ClientConfiguration ? ((ClientConfiguration)config).host : "0", config.port));
		List<Routable> routables;
		if (hasWarming() && notReadyRater.tryDraw() && (routables = warmAny()) != null) { // warming get ra connection mark reclaim thi cu gui va bao loi, dang nao cung close connection
			// warmup chi xet den TPS ko quan tam CC
			try {
				notReadyRater.waitDraw(startBeacon);
			} catch (TimeoutException e) {
				throw new RouteStrategyException(this, "waitDraw failed on warming connection");
			}
		} else {
			routables = selectRoutesWithCached();
		}
		return routables != null ? routables : new ArrayList<>();
	}

	protected abstract String statistics();

	public String report() {
		return String.format("RouteStrategy: %s, connRefs: %d, Warming: %s, Ready: %d, NotReady: %d, strategy: %s",
			this.getClass().getSimpleName(),
			connectionsRef.size(),
			warming.values().stream().map(Routable::warmingUpTPS).collect(Collectors.toList()),
			destMap.size(), notReadiers.size(), statistics());
	}

	public Set<Endpoint> getReadyEndpoints() {
		return destMap.values().stream()
			.map(Routable::getEndpoint)
			.collect(Collectors.toSet());
	}

	public void pressAll() {
		destMap.values().forEach(routable -> {
				try {
					routable.getConnMode().getTps(); // call getTps to trigger sense connection pressWithCache
				} catch (NullPointerException ignored) {
				} // NullPointerException if channel or connModeRef null
			}
		);
	}

	public enum STRATEGY {
		ROUND_ROBIN,
		LATENCY_OF_SERVICE,
		BLUE_GREEN,
		WEIGHTED_BLUE_GREEN,
		ROLLING,
		CANARY,
		INPLACE
	}


	protected STRATEGY type;
	protected StrategyConfigure routeConfig;

	public String getClientId() {
		return routeConfig.clientId;
	}

	/**
	 * part for define BusinessManager
	 * BusinessManager remember key map for addressed routing
	 * BM self manages connection under its serviceName
	 * Connections map: connectionId then translate into ConnectionWrapper
	 * BusinessManager quan ly connections cua 1 serviceName, moi connection se chi thuoc ve 1 serviceName
	 * * viec chia TPS cho WarmupConnectionMode can duoc thuc hien boi 1 FrontPressure tuong duong 1 strategy,
	 * * 	nhung do TPS thap, nen chi su dung round robin try draw va next connection la duoc, khong can phuc tap
	 */
	public List<Routable> warmAny() {
		// roundRobin select connection nao khong ready va draw duoc thi tra ve
		// chung cho tat ca strategy
		return hasWarming()
			? warming.entrySet().stream().filter(e -> warming.get(e.getKey()).tryAccquire()).map(Map.Entry::getValue).collect(Collectors.toList())
			: null;// neu khong co warmup thi tra ve connection fake, sau do khi su dung connection nay se exception
	}

	public STRATEGY getType() {
		return type;
	}

	public synchronized void setRouteConfig(StrategyConfigure configure) {
		routeConfig = configure;
		refresh();
	}


	/**
	 * select connection based on destMap of strategy, this is grand select from scratch rebuild cacheSortList
	 * implement in here:
	 * TPS control,
	 * Concurrent control,
	 * Counter countrol,
	 * QoS control,
	 * Limit connection (ConnectionBlocker) (reduce from 1:n to 1:m<n, m1#m2#...mk)
	 *
	 * @return Connection (ready for send, after this return, connection will be used to flush data)
	 * @throws StrategyException: neu khong co connection de chon
	 */

	public synchronized List<Routable> scratchSelectRoutes() throws RouteStrategyException {
		rebuildCacheSortList();
		return selectRoutesWithCached();
	}

	/**
	 * select from cached or from sortList, kinda filters apply, this comes with cached flag
	 *
	 * @return
	 * @throws RouteStrategyException
	 */
	public abstract List<Routable> selectRoutesWithCached() throws RouteStrategyException;

	protected CopyOnWriteArrayList<Routable> cachedSortList = new CopyOnWriteArrayList<>();

	protected CopyOnWriteArrayList<Routable> rebuildCacheSortList() {
		CopyOnWriteArrayList<Routable> cachedSortList1 = new CopyOnWriteArrayList<>(destMap.values()); // create this var to set and return the same object
		cachedSortList = cachedSortList1;
		return cachedSortList1;
	}

	/**
	 * TODO: hien tai chua co route by key-IP, nen trong routestrategy, resolve = any
	 */
	public List<Routable> resolve(String key) throws RouteStrategyException {
		return selectRoutesWithCached();
	}

	public abstract static class RouteEngine {
		public static String createStringDetail(List<Routable> src) {
			return src.stream().map(r -> String.format("%d%%%d@%s", r.getPeerLoad() / 100, r.getSentCC(false), r.getEndpoint().ip)).collect(Collectors.joining(", "));
		}
	}

	protected RouteStrategy(PeerConfig config, StrategyConfigure configure, Map<String, Routable> connections) {
		this.config = config;
		this.connectionsRef = connections;
		this.routeConfig = Objects.requireNonNull(configure, "Cannot create RouteStrategy with Null config");
	}
}

