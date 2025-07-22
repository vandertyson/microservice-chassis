package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.common.datatype.CollectionUtils;
import com.viettel.vocs.common.datatype.FuseBoolean;
import com.viettel.vocs.common.log.LogAccumulator;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.ServiceStatus;
import com.viettel.vocs.microchassis.connection.client.ClientConnection;
import com.viettel.vocs.microchassis.connection.client.ClientStatus;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.mesh.ClientMeshConfig;
import com.viettel.vocs.microchassis.connection.config.mesh.MeshConfig;
import com.viettel.vocs.microchassis.connection.dns.HostNameResolver;
import com.viettel.vocs.microchassis.connection.dns.ServiceMonitor;
import com.viettel.vocs.microchassis.connection.event.ClientEvent;
import com.viettel.vocs.microchassis.connection.event.ConnectionEvent;
import com.viettel.vocs.microchassis.connection.exception.*;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.*;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.CenterLBR;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.LBRAdapter;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.StandaloneLBR;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.EndpointHolder;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.ServiceMonitorable;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.route.*;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.serviceRegistry.client.LbrClient;
import com.viettel.vocs.microchassis.util.MonitorManager;
import io.netty.buffer.ByteBufAllocator;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

interface UpdateSite {
	Logger logger = LogManager.getLogger(Site.class);

	Routable select(TimeUtils.NanoBeacon startBeacon) throws RouteStrategyException, SendTimeout;

	Routable select(TimeUtils.NanoBeacon startBeacon, String key) throws RouteStrategyException, SendTimeout;

	String getUpdateHostName();

	String getMonitorID();
}

public class Site<Conf extends ClientConfiguration> implements UpdateSite, ServiceMonitorable, SiteAPI {
	/**
	 * Old NettyClient representator for update sites,
	 * when NettyClient(UpdateSite) created, it presents for main update controller
	 * and Site are minions for pair hostnames to satisfy each client handle 1 hostname
	 * Application can create multiple NettyClient, which can be other Site, it doesn't matter
	 * requests are sent to controller (NettyClient), then distributed by strategy to Site(s)
	 * When routing, Site offer its ConnectionWrapper'(s) to main NettyClient,
	 * then main NettyClient handle the call and response to application transparently
	 *
	 * @author tiennn18
	 * Connection is at scope of Site, not NettyClient, any event handle for connection do at Site
	 * updateSite use same business handler with mainSite
	 * scope of connections map is private to prevent NettyClient (mainSite) access to connections of updateSite
	 * for routing, route by updater.getConnection( site will be get in here and return connection to send )
	 */

	private static final AtomicInteger connectionIdCounter = new AtomicInteger(0);
	/**
	 * DNS group
	 * 1. resolvedEndpoints: raw resolve from DNS
	 * then diff to find new appeared, enqueue to newDiscoveredNames
	 * 2. newDiscoveredNames: diff event from snapshot of DNS (resolvedEndpoints) add to queue to guarantee the order of time it appeared
	 * then process to map of newProcessingResolvedEndpoint to reduce the duplicate
	 * 3. newProcessingResolvedEndpoint: map of Endpoint to process diff logic with connected to make desired map
	 * if map 1 disappear some item, it will directly removed from 3 by key, once for all duplicate events
	 * 4. desiredToConnect: map of Endpoint to truly connect, reduced by connected and establishing
	 * 1 endpoint in desiredToConnect equal 1 (or more than 1 if concurrent trigger), call for a chain to continuously decrea the counter, to create connection => control the ignite
	 * 5. establishingEndpointCounterMap: count the number of connection in job connCreatePool
	 * count the creating progress of establishing connection => control the redundant
	 */
	private Set<Endpoint> resolvedEndpoints = new CopyOnWriteArraySet<>(); // for null safe
	private final Queue<Endpoint> newDiscoveredNames = new ConcurrentLinkedQueue<>(); // consumed by putIfAbsent so can consume all duplicate
	private final Map<String, Endpoint> newProcessingResolvedEndpoint = new ConcurrentHashMap<>(); // Pair<Endpoint, Integer> if has result, null if avail for ask, false if claimed for asking, true for asked
	private final AtomicReference<Map<Endpoint, AtomicLong>> desiredToConnect = new AtomicReference<>();
	private final Map<String, AtomicInteger> establishingEndpointCounterMap = new ConcurrentHashMap<>(); // scope of site

	// end of DNS group
	/**
	 * @param connections: o day la cac connection cap quan ly, co the chet, cho ChannelMonitor renew
	 * connections nay khong tham gia truc tiep vao routeStrategy ma chi la dau vao,
	 * sau do se duoc phan loai bang ham classify lien tuc hoac trong moi lan su dung
	 */
	private final Map<String, Routable> connections = new ConcurrentHashMap<>();
	private final Set<Endpoint> runningCreateConnJob = new CopyOnWriteArraySet<>();
	private final LBRAdapter negotiator;
	private final int connMaxCreate;


	@Getter
	protected final Endpoint endpoint; // this is represent, kinda reflex of config, not affect the DNS resolve process which use config.host -> hostName()

	protected final Conf config;
	protected final String hostName; // dung cai nay thay cho config.host

	// Site owned props
	@Getter
	protected final ClientMeshConfig<Conf> mesh;
	@Getter
	protected final AtomicReference<RouteStrategy> routeStrategyRef; // may be null before init, only 1 strategy is set, new Site to change strategy
	protected MonitorManager mon = MonitorManager.getInstance();
	@Getter protected MainSite<?, ?, Conf, ?> mainSite; // set mainSite right after constructor or super of NettyClient

	private Set<Endpoint> composeEndpoints(Set<String> ips) {
		try {
			return ips.stream()
				.map(ip -> Endpoint.newEndpoint(config.host, ip, config.port))
				.collect(Collectors.toCollection(CopyOnWriteArraySet::new));
		} catch (Exception ex) {
			logger.error(ex, ex);
			return null; // null to distinct error with empty resolve result
		}
	}

	@Override
	public String getHostName() {
		return hostName;
	}

	public Conf getConfig() {
		return config;
	}

	public int getPort() {
		return config.port;
	}

	public String getId() {
		return config.id;
	}


	private RouteStrategy createDefaultRouteStrategy() {
		boolean isHttp = ChassisConst.SupportVersion.HTTP.equals(config.getProtocolName());
		if (config.routeStrategy == null) config.routeStrategy = isHttp ? new RCP() : new RR();
		if(isHttp){
			HttpClientOptions hConf = (HttpClientOptions) config;
            if (hConf.negotiable) {
                config.routeStrategy = new RR();
            } else { // force H1 or H2
                config.routeStrategy = ChassisConst.SupportVersion.HTTP2_0.equals(hConf.initProto) ? new RR() : new RCP();
            }
        }
		config.routeStrategy.clientId = config.id;
		if (config.routeStrategy instanceof RCP)
			return new CachedPoolRouteStrategy(config, config.routeStrategy, connections, this);
		else if (config.routeStrategy instanceof RPL)
			return new PooledRouteStrategy(config, config.routeStrategy, connections);
		else if (config.routeStrategy instanceof BTC)
			return new BTCRouteStrategy(config, config.routeStrategy, connections);
		else if (config.routeStrategy instanceof BCC)
			return new CCRouteStrategy(config, config.routeStrategy, connections);
		else if (config.routeStrategy instanceof LCC)
			return new LeastCCRouteStrategy(config, config.routeStrategy, connections);
		else if (config.routeStrategy instanceof RR)
			return new RoundRobinRouteStrategy(config, config.routeStrategy, connections);
		else return new RoundRobinRouteStrategy(config, config.routeStrategy, connections); // default
	}

	public int countConnections() {
		return connections.size();
	}

	public long countConnection(Endpoint endpoint) {
		return EndpointHolder.countEndpoint(connections.values(), endpoint);
	}

	public long countEnabled() { // all LB
		return connections.values().stream().filter(Routable::isEnable).count();
	}

	public long countEnabled(Endpoint endpoint) { // all LB count connected to a server instance
		return EndpointHolder.filterEndpoint(connections.values(), endpoint).filter(Routable::isEnable).count();
	}

	public long countConnected() {
		return connections.values().stream()
			.filter(Routable::isConnected)
			.count();
	}

	public long countConnected(Endpoint endpoint) { // all LB count connected to a server instance
		return EndpointHolder.filterEndpoint(connections.values(), endpoint).filter(Routable::isConnected).count();
	}

	@Override
	public Map<Endpoint, Long> countEndpoints() {
		return connections.values().stream()
			.map(Routable::getEndpoint)
			.filter(Objects::nonNull)
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	}

	@Override
	public Map<Endpoint /*IP*/, Long> countConnectedEndpoints() {
		return routeStrategyRef.get().getReadyEndpoints().stream()
			.filter(Objects::nonNull)
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	}

	@Override
	public Map<String /*IP*/, Long> countConnectedIps() {
		return routeStrategyRef.get().getReadyEndpoints().stream()
			.filter(Objects::nonNull)
			.map(e -> e.ip)
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	}

	@Override
	public Set<Endpoint> getConnectedEndpoints() {
		return connections.values().stream()
			.map(Routable::getEndpoint)
			.collect(Collectors.toSet());
	}

	@Override
	public Set<Routable> getConnections() {
		return new LinkedHashSet<>(connections.values()); // thread safe for access and alter
	}

	private int countEstablishing(Endpoint e) {
		return establishingEndpointCounterMap.getOrDefault(e.toString(), new AtomicInteger(0)).get();
	}

	private String logEstablishing() {
		return String.format("{desired: %d [%s], establishing: %d, [%s]}]",
			newProcessingResolvedEndpoint.size(), String.join(", ", newProcessingResolvedEndpoint.keySet()),
			establishingEndpointCounterMap.size(), establishingEndpointCounterMap.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(", ")));
	}

	@Override
	public List<Routable> getNotDeleteMarker() {
		return connections.values().stream().filter(conw -> !conw.isDeleteMarker()).collect(Collectors.toList());
	}

//	@Override
//	public List<Endpoint> selectEndpoints() { // select from processingResolvedMap to take responded server then sort
//		if (!newProcessingResolvedEndpoint.isEmpty()) {
////			List<Pair<Endpoint, Integer>> serverCountPairs = IntStream.range(0, serverConnCounts.size())
////				.mapToObj(idx -> new ImmutablePair<>(selectableEndpoints.get(idx), serverConnCounts.get(idx)))
//			List<Endpoint> connectableServers = newProcessingResolvedEndpoint.values().stream() // this mean ask all resovlved endpoint for serverCount -> create much job
//				.filter(Endpoint::isReadyToConnect) // remove impolite server drop connection while asking value -1 and -2 havent asked
//				.collect(Collectors.toList());
//			logger.info("[{}] Selectable {}: {}", getId(), connectableServers.size(), newProcessingResolvedEndpoint.values().stream().map(Endpoint::status).collect(Collectors.joining(",")));
//			if (!connectableServers.isEmpty()) {
//				connectableServers.sort(Comparator.comparing(e -> e.serverCount)); // asc
//				// filter out largest if all result is not largest item, and under limits
//				//  ie1: 0121102 -> 01110
//				//  ie2: 22222 -> 22222
//				int maxConn = connectableServers.get(connectableServers.size() - 1).serverCount;
//				if (connectableServers.get(0).serverCount != maxConn) {
//					connectableServers = connectableServers.stream()
//						.filter(e -> e.serverCount < maxConn)
////						.limit(mesh.maxConnection - countConnected())
//						.collect(Collectors.toList());
//				} // else // 1 item list or all item equal max like ie2
//				// this may lead to bottleneck, if servers configured with many number of allow conn
//				//  -> always try with lowest allow conn
//				//  -> but if server full of conn, it wont allow ask, then reply with -1 to be filted out, so connCount in list are all came from redundant servers
//				//   -> try from less to most, this logics is ok, and no need to limit maxConnection to test all until connected
//				return connectableServers;
//			}
//		}
//		return new ArrayList<>() {
//		}; // empty
//	}

	public ByteBufAllocator getBytebufAllocator() {
		return mainSite.getBytebufAllocator();
	}

	public boolean resolvedContainIp(String ip) {
		return resolvedEndpoints.stream().anyMatch(e -> e.ip.equals(ip));
	}

	public int nResolved() {
		return resolvedEndpoints.size();
	}

	@Override
	public Set<String> resolveDNS() { /*
	[new] trigger resolveDNS for this hostname, other ServiceDiscoverable can utilize this result for updateDNS(...)
	else, this can be trigger at each site at constructor to get first sight of DNS for first connection boot up
	*/
// [OLD] trigger reresolveDNS interval for all site, call at mainsite
//		logger.info("[{}] trigger resolveDNS all site", getId());
//		return updater.getSites().values().stream().allMatch(Site::reresolveDNS); // call all resolveDNS include this mainSite
		int resolveFailed = 0;
		Set<String> listResolved;
//		try {
//			return
//		} catch (Exception e) {// catch all roi
		while ((listResolved = HostNameResolver.doResolveDNS(hostName)) == null) {
			if (resolveFailed++ < ChassisConfig.RouteConfig.ServiceDiscoveryConfig.maxRetryResolve.get()) {
				logger.info("[{}][Max retry reach. Mark as service down]{hostname={}}", getId(), hostName);
				if (ManoConfigure.getConfig(hostName) == null // on no upgrade
					|| ManoConfigure.getWeightFromManoConfig(hostName) != 0 // upgrading with load weight
				) mainSite.eventHandler //check if current has no weight on service then not trigger or resolve // case dang resolve dc thi khong resolve dc nua
						.triggerEvent(new ClientEvent(ClientEvent.EventType.DISCOVERY_FAIL, this, String.format("Connection ID %s can not resolve IP for %s after %d retries", config.id, hostName, ChassisConfig.RouteConfig.ServiceDiscoveryConfig.maxRetryResolve.get())))
						.triggerEvent(isConnected() ? null : new ClientEvent(ClientEvent.EventType.CONNECT_FAIL, this, String.format("Connection ID %s failed to create connection to  %s", config.id, hostName))
						);
				// site nao ban event site do
				break;
			}
			// resolve fail and keep retry
		}
		mainSite.eventHandler.triggerEvent(new ClientEvent(ClientEvent.EventType.DISCOVERY_SUCCESS, this, "name " + hostName));
		return listResolved;
	}

	@Override
	public void updateDNS(Set<String> resolvedIps) {
		/**
		 * logic: DNS tin cay duoc,
		 * 	neu IP bien mat khoi DNS ma connection con dang song thi force close
		 * 		=> zombie case
		 * 	neu IP chua bien mat khoi DNS ma connection close -> retry connect
		 */
		int nResolvedBeforeUpdateDns = nResolved();
		resolvedEndpoints = composeEndpoints(resolvedIps);
		logger.info("[{}][Host name resolved]{name={}, count={}, ips={}}", getId(), hostName, nResolved(), resolvedEndpoints);
		CollectionUtils.DiffResult<Endpoint> endpointDiffResult = CollectionUtils.diff(getConnectedEndpoints() /*actual*/, resolvedEndpoints /*desired*/);
		endpointDiffResult.applyIfDiff(new CollectionUtils.Apply1T<>() {
			@Override
			public void onNewApply(Endpoint appeared) {
				if (!newDiscoveredNames.offer(appeared)) logger.info("Full queue newDiscoveredNames");
			}

			@Override
			public void onDisappearApply(Endpoint disappeared) {
				logger.info("[c:{}->{}] Server {} disappeared from DNS", getId(), hostName, disappeared);
				// clear Connections of down ip
				if (!config.dnsTrust || (/*trust,  on case many to 0*/
					nResolvedBeforeUpdateDns > 1 // trust case reduce 1 ip to 0 ip
						&& nResolved() == 0 // case dang co nhieu hon 1 IP, tu dung ve 0, thi no trust
				)) {
					logger.info("[{}] Connections is active but IP is not resolved. No trust DNS, keep. endpoint={}", getId(), disappeared);
				} else { // trust DNS
					connections.values().stream()
						.filter(conn -> Objects.equals(conn.getEndpoint(), disappeared))
						.forEach(conn -> {
							logger.info("[{}] Connection {} is active but IP is not resolved. Trust DNS, force close. endpoint={}", getId(), conn.getMonitorID(), conn.getEndpoint());
							deregister(conn, false);
							onServiceDown(conn);
						});
				}
				while (newDiscoveredNames.remove(disappeared)) {
				}
				; // do check utilization 90% nen co the con sot lai endpoint trong queue newDiscoveredNames
				removeEndpointFromInit(disappeared);
			}

			@Override
			public void onExistedApply(Endpoint endpoint) {
				// ignore
			}
		});
		if (logger.isDebugEnabled() && endpointDiffResult.disappear.size() > 0)
			logger.info("[{}]Finish process service down. id={}, serviceName={}, ip={}, connection={}, resolved={}, removeResolved={}", getId(),
				config.id, hostName, endpointDiffResult.disappear.size(), connections.size(), nResolved(),
				endpointDiffResult.disappear.stream().mapToInt(downer -> getConnectedEndpoints().removeIf(endpoint -> Objects.equals(endpoint, downer)) ? 1 : 0).sum());

		pullNewEndpoints(); // active pull after offer for other thread consume to connect immediately
		switch (mesh.mode) {
			case CENTER_LIMIT:
			case ASK_LIMIT:
				break;
			case FULL_MESH: // Fullmesh will utilize raw newDiscoveredEndpoints queue without count pairs
				mesh.maxConnection = nResolved() * connMaxCreate; // for fullmesh limit
				break;
		}
	}

	private synchronized void pullNewEndpoints() { // pull from newDiscovered to mapProcessing, until it full load SharedThreadPool
		countEndpoints().forEach((e, c) -> checkOverCreateEndpoint(e, c /*connected*/ + /*connecting*/ runningCreateConnJob.stream().filter(i -> Endpoint.equalsWithoutDns(i, e)).count()));
		Endpoint e;
		while ((e = newDiscoveredNames.poll()) != null) {
			Endpoint finalE = e;
			if(resolvedEndpoints.contains(e)) newProcessingResolvedEndpoint.computeIfAbsent(e.toString(), k -> {
				logger.info("On Compute for {} newProcessingResolvedEndpoint has {}", finalE.toString(), logEstablishing());
				return finalE;
			});
		}
	}

	@Override
	public void seedCreateConnJobs(int createOnDemanConns) {
		/**
		 * createOnDemanConns
		 *  = 0 means create from DNS
		 *  > 0 mean create from strategy on demand
		 *
		 * new ver of server accept all conn and response nServerConn after first ping, then client drop
		 * this led server need to separate group of accepted channel, and group of willing to deny, then if channel in accepted group ask for nServerConn, it will received by a valid number, and denied group will receive -1
		 * 	in case of large scale, all client will connect and full accepted pool -> all received -1 -> all close -> cyclic state
		 */
		if (!isAllowNewConnection()) return; // skip create for quick cycle
		Map<Endpoint, Long> desired;
		establishingEndpointCounterMap.entrySet().stream() // clean up of previous interval
			.filter(entry -> entry.getValue() == null || entry.getValue().get() == 0)
			.map(Map.Entry::getKey)
			.collect(Collectors.toList())
			.forEach(establishingEndpointCounterMap::remove); // remove key for other op using keySet()
		if (negotiator instanceof CenterLBR) {
			mesh.read(((CenterLBR) negotiator).getClient(getId())); // update before iterate create connection
			logger.info("[{}] Connection destinations {}", getId(), mesh.destinations);
			desired = mesh.destinations.entrySet().stream().map(e -> Map.entry(Endpoint.newEndpoint(e.getKey() /*ip*/, config.port), e.getValue()))
				.collect(Collectors.toMap(Map.Entry::getKey, e -> { /*e.getValue() k*/
					Long k = e.getValue();
					long desiredToConnect = Long.min(config.connectionPerIP, k);
					return countEnabled(e.getKey()) >= desiredToConnect ? 0L : desiredToConnect - countEstablishing(e.getKey());
				}));
//			} else if (negotiator instanceof StorageLBR){
		} else if (createOnDemanConns == 0) { // StandaloneLBR
			desired = newProcessingResolvedEndpoint.values().stream().parallel().filter(e -> resolvedEndpoints.contains(e)).collect(Collectors.toMap(e -> e, e -> (long) config.connectionPerIP - countConnected(e) - countEstablishing(e)));
			logger.info("[{}] StandaloneLBR DNS {} => create {}", getId(), logEstablishing(), desired.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(", ")));
		} else { // create on demand (usually came from HTTP1.1)
			desired = resolvedEndpoints.stream().parallel().collect(Collectors.toMap(e -> e, e -> (long) config.connectionPerIP - countEstablishing(e)));
			ConcurrentHashMap<Endpoint, AtomicLong> toCreateMap = new ConcurrentHashMap<>();
			logger.info("Redun map " + desired.values()); // desired map
			if (desired.size() != 0) {
				List<Long> connCountStages = desired.values().stream().sorted().collect(Collectors.toList());
				// greater than 1, do balancing
				if (connCountStages.size() == 1) {
					int finalNewConns = createOnDemanConns;
					toCreateMap = desired.keySet().stream().collect(Collectors.toMap(e -> e, e -> new AtomicLong(finalNewConns), (a, b) -> b, ConcurrentHashMap::new));
				} else {
					for (int i = 0; i + 1 < connCountStages.size() && createOnDemanConns > 0; i++) {
						long countNeedToImprove = connCountStages.get(i); // min of redundantMap
						long stageDif = connCountStages.get(i + 1) - countNeedToImprove;
						toCreateMap = desired.entrySet().stream().filter(e -> e.getValue() == countNeedToImprove).collect(Collectors.toMap(Map.Entry::getKey, e -> new AtomicLong(0), (a, b) -> b, ConcurrentHashMap::new));
						desired.keySet().removeAll(toCreateMap.keySet());
						long toFill = stageDif < createOnDemanConns ? stageDif : createOnDemanConns;
						createOnDemanConns -= toFill; // total add to map on this round
						long toAdd = (long) Math.ceil((double) toFill / toCreateMap.size()); // to add to each E
						for (Map.Entry<Endpoint, AtomicLong> createItem : toCreateMap.entrySet()) {
							if (toFill == 0) break; // for early end
							toAdd = Math.min(toFill, toAdd);
							toFill -= toAdd;
							createItem.getValue().addAndGet(toAdd);
						}
						if (createOnDemanConns == 0) break;
					}
				}
				desired = toCreateMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
			} // else // all endpoint havent full initialized yet. use empty desiredToConnectMap to set desired
			logger.info("[{}] StandaloneLBR cached create on demand {} {} => create {}", getId(), nResolved(), logEstablishing(), desired.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(", ")));
		}
		Map<Endpoint, AtomicLong> desiredToConnectMap = desired.entrySet().stream().filter(e -> e.getValue() > 0).collect(Collectors.toMap(Map.Entry::getKey, e -> new AtomicLong(e.getValue())));
		desiredToConnect.set(desiredToConnectMap);
		desiredToConnectMap.forEach((desiredE, kCounter) -> {
			if (runningCreateConnJob.stream().noneMatch(creatingE -> Objects.equals(desiredE, creatingE)) && desiredE.claimToConnect() /*claimable by this job*/) { // seed job create, once per endpoint, co the add nhieu hon 1 job cho 1 endpoint
				runningCreateConnJob.add(desiredE); // moi 1 job add 1 phat, doan tren check utilize cua pool roi, nen job nao duoc vao den day thi se +1 hop ly
				String jobName = String.format("EndpointDesiredCreateChain{client=%s endpoint=%s}", config.id, desiredE);
				if (logger.isDebugEnabled()) logger.info("Seed createConnJob {}", jobName);
				lastSeedCreateConn.set(TimeUtils.nowNano());
				ChassisThreadManager.createConnPool.execute(() -> { // when this job run, first check it still in desired
					long startJobTime = TimeUtils.nowNano();
					try {
						AtomicLong desireCounter = desiredToConnect.get().get(desiredE);
						if (desireCounter == null || desireCounter.get() <= 0L || !resolvedEndpoints.contains(desiredE)) {
							if (logger.isDebugEnabled())
								logger.debug("Rejected by desireMap createConnJob {}", jobName);
							return; // skip if not desired anymore, maybe already removed or changed to another endpoint
						} else if (logger.isDebugEnabled()) logger.debug("Start createConnJob {}x {}", desireCounter.get(), jobName);
						// begin the create chain
						while (isAllowNewConnection(desiredE) && desireCounter.decrementAndGet() >= 0) {
							Routable conn;
							try {
								// do job nay chay async, nen co the vuot, chi register neu con slot tai clientD
								AtomicInteger establishCounter = establishingEndpointCounterMap.computeIfAbsent(desiredE.toString(), epString -> new AtomicInteger(0));
								establishCounter.incrementAndGet();
								try {
									conn = mainSite.createConnection(Site.this, desiredE, mainSite.eventHandler, true);// khi nao len channel thanh cong thi register trong onChannelRenew
								} finally { // after connect, conn is registered but maybe in not usable state, need a promise to check enable Fuse change
									establishCounter.decrementAndGet();
								}

								if (conn != null) { // establish conn nao thi check dua vao su dung luon, khong gom gop de cho check 1 lan
									// check usable is not count as createConn job // this usable fuse flip only once
									while (!conn.checkUsable()) TimeUtils.waitSafeMili(1); // duration is 1ms
									if (conn.getChannel() != null)
										routeStrategyRef.get().classifyConnection(conn); // reclassify after usable if still have channel
								}
							} catch (Throwable e) {
								logger.error("Error in 1 createConnJob", e);
							}
						}
					} finally { // end of job
						runningCreateConnJob.remove( // remove 1
							desiredE.failedToConnect());  // release for next ServiceMonitor round retry create
						if (logger.isDebugEnabled())
							logger.debug("Finish createConnJob {} after {}ms", jobName, TimeUtils.miliPassed(startJobTime));
					}
				});
			}
		});
	}

	private long countNotReclaimed() {
		return getNotDeleteMarker().size();
	}

	private final AtomicLong lastSeedCreateConn = new AtomicLong(TimeUtils.nowNano());

	public long getLastSeedCreateConn() {
		return lastSeedCreateConn.get();
	}


	public void fetchStatus(ClientStatus status) {
		status.getServiceStatus().computeIfAbsent(hostName, k -> new ServiceStatus(config.port));
		ServiceStatus serviceStatus = status.getServiceStatus().get(hostName);
		serviceStatus.getResolvedIP().addAll(resolvedEndpoints.stream().map(e -> e.ip).collect(Collectors.toList()));
		connections.values().forEach(conn -> serviceStatus.getConnected().put(conn.ip(), conn.isConnected()));
	}

	@Override
	public Map<String, Routable> getOpenConnections() {
		return connections.entrySet().stream().filter(conn -> !conn.getValue().isClosed())
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	public boolean routeStrategyConnected() {
		return routeStrategyRef.get().isConnected();
	}

	public boolean isSendable() {
		return routeStrategyConnected() && isConnected();
	}

	public boolean isConnected() {
		return routeStrategyConnected();
	}

	public String getConnectionInfo() {
		return String.format("{host=%s, port=%d, resolved={%s=%s}, strategy=%s, countConnection=%d, connected=%d, connections=%s}", hostName, config.port, hostName, resolvedEndpoints, routeStrategyRef.get().report(), connections.size(), countConnected(), countEndpoints());
	}

	public String getConnectionDebug() {
		return String.format("[Client connection detail]{id=%s, resolved={%s=%s}, connected={%s}, nullChannel=[%s], booleanConnected={%s}, isActive/writable={%s}}", config.id, config.host, connections.values().stream().map(f -> toString())
			.collect(Collectors.joining(", ")), getOpenConnections().values().stream().map(Routable::getId)
			.collect(Collectors.joining(" ")), connections.values().stream().filter(conn -> conn.getChannel() == null).map(Routable::getId)
			.collect(Collectors.joining(", ")), connections.values().stream().map(c -> c.toString() + ":" + c.isEnable())
			.collect(Collectors.joining(" ")), connections.values().stream().filter(conn -> conn.getChannel() != null)
			.map(f -> String.format("%s:%s/%s(w%db|uw%db)", f, f.getChannel().isActive(), f.isWritable(), f.getChannel().bytesBeforeWritable(), f.getChannel().bytesBeforeUnwritable()))
			.collect(Collectors.joining(" ")));
	}

	public void refresh() {
		RouteStrategy siteRouteStrategy = routeStrategyRef.get();
		if (siteRouteStrategy != null) {
			siteRouteStrategy.checkReclassify();
			if (logger.isDebugEnabled())
				logger.info("Client {} {} after reclassify: {}", getId(), hostName, routeStrategyRef.get().report());
			siteRouteStrategy.intervalRefresh(); // TODO check netty connect time of new connection to reduce this scan
			siteRouteStrategy.isolationCheck();
		}
		/** revokeOverCreateConnections()
		 * this need to run frequently to soon retrieve over create conns
		 * cannot rely on ServiceMonitor loop -> use lbMonitor loop
		 */
		long nDelete = Long.max(countNotReclaimed() - mesh.maxConnection, 0);
		if (nDelete > 0) {
			// mark new connections
			logger.info(String.format("[%s]Need to delete %d conn. Has %d/%d",
				getId(), nDelete, countNotReclaimed(), mesh.maxConnection));
			// avail for reclaim
			List<Routable> activeConns = getNotDeleteMarker();
			logger.info(String.format("[{}] NotReclaimed %d", activeConns.size()), getId());
			//			// should reclaim first
			//			List<Routable> newReclaimConns = activeConns.stream()
			//				.filter(conn -> RequestTimeoutMonitor.getMapTime(conn.getChannel()) == null)
			//				.limit(nDelete)
			//				.peek(conn -> conn.setReclaim(true))
			//				.collect(Collectors.toList());
			//			logger.info("[{}] Safe Reclaimed " + newReclaimConns.size(), getId());
			//			long nMarkLeft = nDelete - newReclaimConns.size();
			//			if (nMarkLeft > 0) {
			activeConns.stream()
//					.filter(conw -> !newReclaimConns.contains(conw))
				.limit(nDelete)
//					.limit(nMarkLeft)
				.forEach(conw -> {
					conw.setDeleteMarker(true);
					logger.info(String.format("[{}] Mark Reclaimed %s", conw.getId()));
				});
			// resolve to delete ok to delete connections
			connections.values().stream()
				.filter(conn -> conn.getChannel() != null && conn.isDisableRoute()) // marked delete and cc 0 => routestrategy cannot route to here next msg, can force close immediately
				.forEach(conn -> {
					logger.info(String.format("[{}]Reclaiming ConW%s", conn.getId()), getId());
					deregister(conn, false);
				});
		}
		// end of revokeOverCreateConnections()
		pullNewEndpoints();
		negotiator.updateClient(this); // to update to lbr that there are some connection change sendable status/disconnect, to recalculate
	}
	//		/**
	//		 * cac truong hop cham DNS:
	//		 * 	TH1: resolve lan dau khong co -> resolve lan tiep theo thay co - > initConnection job
	//		 * 	~> TH2: resolve lan dau co IP moi -> initConnection job -> resolve lai thay khong con -> xoa
	//		 */

	protected void revokeDisconnected(Function<Routable, Boolean> additionalCondition) {
		connections.values().stream()
			.filter(conn -> !conn.isConnected() && (additionalCondition == null || additionalCondition.apply(conn)))
			.forEach(conn -> {
				logger.info(String.format("[{}]Close dead conn %s", conn.getId()), getId());
				deregister(conn, false);
			});
	}

	public void deregister(Routable conn, boolean doneConnDeregister) {
		if (doneConnDeregister) siteDeregister(conn);
		else conn.deregister();
	}

	private void siteDeregister(Routable conn) {
		if (conn != null) {
			conn.waitFlushAllMsg();
			String connId = conn.getId();
			routeStrategyRef.get().removeConn(connId); // remove out of routing before delete from conns
			connections.remove(connId);
			routeStrategyRef.get().refresh(); // refresh chu dong sau khi co thay doi list connection
			// remove from management for isolated load first then close
			negotiator.updateClient(this); // to update to lbr that there are some connection change sendable status/disconnect, to recalculate
			logger.info("[{}] Removed conn on ctx={}", getId(), conn);
			conn.close(); // close handle Exception roi
		}
	}

	public synchronized boolean register(Routable conn) {
		if (conn != null) {
			if (connections.containsKey(conn.getId()))
				return true; // registered, skip with true, need for onChannelRenew to work with registered connections
			Endpoint upEndpoint = conn.getEndpoint();
			if (isAllowNewConnection(upEndpoint)) { //do qua trinh create connection la async, nen can check moi khi register moi // both Bidirect and Fullmesh
				// if (upEndpoint != null) { // always not null bcuz final
				logger.info("[{}][Service up]{hostname={}, resolvedCount={}, up={}}", getId(),
					hostName, nResolved(), upEndpoint);
				mainSite.eventHandler.triggerEvent(new ClientEvent(ClientEvent.EventType.DISCOVERY_SUCCESS, this, "Service " + hostName + " resolved successfully. 1 address found"));
				// }

				logger.info("[{}] Registered new conn to {id={}, addr={}, connsSize={}}",
					getId(), config.id, upEndpoint, connections.size());
				connections.put(conn.getId(), conn);
				routeStrategyRef.get().markAsNotReady(conn); // later to readiers by checkUsable job
				routeStrategyRef.get().refresh(); // refresh chu dong sau khi co thay doi list connection
				if (negotiator instanceof StandaloneLBR)
					checkOverCreateEndpoint(upEndpoint, countConnection(upEndpoint));
				negotiator.updateClient(this); // to update to lbr that there are some connection change sendable status/disconnect, to recalculate
				return true;
			}
		}
		return false;
	}

	private void checkOverCreateEndpoint(Endpoint e, long counted) {
		if (counted >= connMaxCreate) {// when enough StandaloneLBR
			removeEndpointFromInit(e);
		}
	}

	private void removeEndpointFromInit(Endpoint e) {
		newProcessingResolvedEndpoint.remove(e.toString());
		establishingEndpointCounterMap.computeIfPresent(e.toString(), (k, v) -> {
			v.set(0);
			return v;
		});
	}

	/**
	 * allow for whole hostname
	 */
	protected boolean isAllowNewConnection() {
		return connections.size() < mesh.maxConnection;
	}

	/**
	 * allow for specific endpoint, this may create IMBALANCE conn count
	 *
	 * @param e
	 * @return
	 */
	protected boolean isAllowNewConnection(Endpoint e) {
		// check allow
		return isAllowNewConnection() && (e == null || countConnection(e) < connMaxCreate);
	}


	public Routable select(TimeUtils.NanoBeacon startBeacon, String key) throws RouteStrategyException, SendTimeout {
		// function nay da bao gom warmUp
		// warmup TPS da duoc kiem soat o ConnectionWrapper.limiter.mode, chi viec gui vao la duoc
		// khong can draw o day
		// -> se lam notReadyRater > real warmup speed
		// -> lam cho cac connection nao duoc warmup bang select key se end warmMode nhanh hon
		// 	-> sau do notReadyRate cap nhat TPS lai, cac connection checkMode
		//  -> warmAny se khong draw duoc tren cac select key connection -> tang toc end cho cac connection khong select key
		//  -> khong co anh huong, cac yeu to tang toc do deu duoc rater trong tung ConnectionWrapper kiem soat
		RouteStrategy strategySnapshot = routeStrategyRef.get();
		if (strategySnapshot != null) {
			return selectOnListRoutable(startBeacon, strategySnapshot, TimeUtils.nowNano(), strategySnapshot.resolve(key));
		}
		throw new RouteStrategyException(null, "Not yet set routeStrategy to site");
	}

	public Routable select(TimeUtils.NanoBeacon startBeacon) throws RouteStrategyException, SendTimeout {
		/**
		 * uu tien lay warmup connection truoc, con lai gui vao strategy
		 * 	neu draw thanh cong, tuc la co warming up connection dang cho request -> gui den readyController
		 * cac request con lai luon co 1 connection ready de xu ly, nen khong lo bi hold lai
		 *
		 * this method read list of available conns then conclude into final selected
		 */
		RouteStrategy strategySnapshot = routeStrategyRef.get();
		if (strategySnapshot != null) {
			return selectOnListRoutable(startBeacon, strategySnapshot, TimeUtils.nowNano(), strategySnapshot.anyWithWarmPrior(startBeacon)); // keep order of selected
		}
		throw new RouteStrategyException(null, "Not yet set routeStrategy to site");
	}

	@NotNull
	private Routable selectOnListRoutable(TimeUtils.NanoBeacon startBeacon, RouteStrategy strategySnapshot, long selectStart, List<Routable> orderedRoutables) throws NoConnectionForStrategyToSelect, SendTimeout {
		if (orderedRoutables.isEmpty())
			throw new NoConnectionForStrategyToSelect(strategySnapshot);
		List<Routable> acquirables = orderedRoutables.stream().filter(r -> !r.isDeleteMarker()).collect(Collectors.toList());
		for (int retry = 0; retry < config.channelConfiguration.maxRetryAcquire; retry++) {
			Optional<Routable> firstAcquirable = acquirables.stream().filter(Routable::tryAccquire).findFirst();
			if (firstAcquirable.isPresent()) {
				Routable conn = firstAcquirable.get();

				mon.algorithm(selectStart, conn.getSentCC(false), config.routeStrategy.logId);
				long acquireStart = TimeUtils.nowNano();
				try {
					conn.acquire(startBeacon); // hoi thua thai, nhung ko sao, buf after wait, maybe refs be null if channel died. accquire nay giup 2 thread ko di vao send tren cung 1 conn
				} catch (TimeoutException e) {
					throw new SendTimeout(String.format("acquire timeout %s on selected connection %s", startBeacon, conn), (ClientConnection) conn, ConnectionException.CONN_ERROR.TIMEOUT);
				}
				mon.acquire(acquireStart, routeStrategyRef.get().countReady(), config.routeStrategy.logId);
				return conn;
			}
			TimeUtils.waitSafeTick(); // retry acquire all after a tick
		}
		throw new NoConnectionForStrategyToSelect(strategySnapshot);
	}

	@Override
	public String getUpdateHostName() {
		return mainSite.getUpdateHostName();
	}

	@Override
	public String getMonitorID() {
		return String.format("%s_%s" /*clientId_siteUpdateHostname*/, getId(), hostName);
	}

	protected void onServiceDown(Routable downCon) {
		try {
			//resolve luon cac alarm ton
			String message = String.format("IP is marked as down. Resolve alarm. connectionID=%s host=%s port=%d channel=%s",
				getId(), getHostName(), getPort(), this);
			logger.info("[{}]{}", getId(), message);
			//triggers for mano
			mainSite.eventHandler
//				.triggerEvent(new ConnectionEvent(ConnectionEvent.EventType.CONNECTION_UP, (ClientConnection) downCon, message))
				.triggerEvent(new ConnectionEvent(ConnectionEvent.EventType.CONNECTION_INIT_SUCCESS, (ClientConnection) downCon, message))
				.triggerEvent(new ConnectionEvent(ConnectionEvent.EventType.SERVICE_NAME_VERIFICATION_SUCCESS, (ClientConnection) downCon, message));
		} catch (Exception ex) {
			logger.error(ex, ex);
		}
	}


	protected void initDNS() { // this must call after eventHandler in mainSite(NettyClient) is defined and add handlers
		Set<String> firstResolved = resolveDNS();
		if (firstResolved != null) {
			updateDNS(firstResolved);
			seedCreateConnJobs(0);
		}
	}


	public Site(Conf originalConfig) {
		config = Objects.requireNonNull(originalConfig, "Site configuration is required");
		if (config.mapDnsIp == null) config.mapDnsIp = new ConcurrentHashMap<>();
		config.patchAuthenServiceName();

		Objects.requireNonNull(config.channelConfiguration, "Channel configuration is required");
		hostName = config.host;

		// for main site
		mesh = new ClientMeshConfig<>(config);
		endpoint = Endpoint.newEndpoint(hostName, config.port); // .patchServiceName(ConnectionManager.getMyDNS());
		if (this instanceof LbrClient) {
			logger.info("[{}] type Site is LbrClient, overwrite Ask", getId());
			mesh.mode = MeshConfig.Mode.ASK_LIMIT;
		}
		negotiator = ConnectionManager.getInstance().getNegtor(
			mesh.mode.equals(MeshConfig.Mode.CENTER_LIMIT) ? config.lbrId : ChassisConst.STANDALONE_LBR_ID
		);
		negotiator.registerClient(this);
		// do not access mainSite prop at constructor, NettyClient is child havent constructed
		routeStrategyRef = new AtomicReference<>(createDefaultRouteStrategy());
		connMaxCreate = Math.max(config.connectionPerIP, config.routeStrategy.maxConnectionPerIp);
		logger.info("TYPE STRAT " + routeStrategyRef.get().getClass().getSimpleName());
	}

	public Site(NettyClient<?, ?, Conf, ?> mainSite, String siteHostName) {
		// for minion site, copy from mainSite vvv
		config = mainSite.config; // included backPressureConfig set clientId
		hostName = siteHostName;
		// for minion site, copy from mainSite ^^^
		if (Objects.equals(siteHostName, mainSite.hostName))
			throw new UpdateSiteException("Duplicate updateSite");
		mesh = new ClientMeshConfig<>(config);
		endpoint = Endpoint.newEndpoint(hostName, config.port); // .patchServiceName(ConnectionManager.getMyDNS());
		if (this instanceof LbrClient) mesh.mode = MeshConfig.Mode.ASK_LIMIT;
		negotiator = ConnectionManager.getInstance().getNegtor(
			mesh.mode.equals(MeshConfig.Mode.CENTER_LIMIT) ? config.lbrId : ChassisConst.STANDALONE_LBR_ID
		);
		negotiator.registerClient(this);
		this.mainSite = mainSite; // can access mainSite props now
		routeStrategyRef = new AtomicReference<>(createDefaultRouteStrategy());
		connMaxCreate = Math.max(config.connectionPerIP, config.routeStrategy.maxConnectionPerIp);
		logger.info("TYPE STRAT " + routeStrategyRef.get().getClass().getSimpleName());
	}

	public void setRouteStrategy(RouteStrategy routeStrategy) { // TODO @Future: need define method to detect strategy change then change limiter, counter, activeCM
		routeStrategyRef.set(Objects.requireNonNull(routeStrategy, "New RouteStrategy is not allow to be null"));
	}


	public void siteClose() {
		logger.info("[Close client]{{}}", this);
		ServiceMonitor.unsetMonitorServices(this);
		negotiator.deregisterClient(getId());
	}

	public boolean checkAndWaitStable(long durationToExitNs) { // this is not a Timeout
		long start = TimeUtils.nowNano();
		FuseBoolean isStable = new FuseBoolean(false);
		AtomicReference<Set<String>> connectedEndpoints = new AtomicReference<>();
		LogAccumulator notStableLog = new LogAccumulator(
			(a, c) -> TimeUtils.waitSafeNano(durationToExitNs / 100),
			a -> logger.info("[x{}]Not stable due to connected {} not match resolved {}", a.getAccumulated(), connectedEndpoints.get(), resolvedEndpoints));
		do {
			connectedEndpoints.set(getConnectedEndpoints().stream().map(Endpoint::toString).collect(Collectors.toSet()));
			if (nResolved() == connectedEndpoints.get().size() &&
				resolvedEndpoints.stream().allMatch(e -> connectedEndpoints.get().contains(e.toString())))
				isStable.fuse();
			if (isStable.get() || TimeUtils.isTimeoutNano(start, durationToExitNs)) break;
			else notStableLog.accumulate();
		} while (!isStable.get());
		notStableLog.logIfAccumulated();
		return isStable.get();
	}

	public void revokeOverCreated(Routable conn) {
		Endpoint endpointRepresent = conn.getEndpoint();
		logger.info("Over create connection to {} reject to register (before close: channelActive={} connected={} conPerIP={})", endpointRepresent, // server drop make channel die /* khong bi server deny */
			isConnected(), countConnected(endpointRepresent), connMaxCreate);
		deregister(conn, false); // bcuz called register(this)
	}
}
