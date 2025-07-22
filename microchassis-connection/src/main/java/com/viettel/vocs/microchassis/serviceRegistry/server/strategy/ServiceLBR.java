package com.viettel.vocs.microchassis.serviceRegistry.server.strategy;

import com.viettel.vocs.common.datatype.CollectionUtils;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRClientInfo;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRMsg;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRServerInfo;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.util.FifoDistinctCollection;
import com.viettel.vocs.microchassis.util.LogDistinctCollection;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 * is a group of isolated service, contains connection that balance between each other, each group is isolated by business and load
 * in here is 1 layer of service
 */
public class ServiceLBR {
	private static final Logger logger = LogManager.getLogger(ServiceLBR.class);
	private final Endpoint endpoint;
	/**
	 * trong moi server co list id gan voi cau hinh
	 * podMap (serverMap cua service name nay) se mesh voi clientMap cua nhieu service name khac theo layout tinh toan
	 */
//	public Set<Integer /*port*/> httpServerPorts = new CopyOnWriteArraySet<>(); // order to fill connection
//	public Set<String /*pod ip*/> serverPodHistory = new CopyOnWriteArraySet<>(); // order to fill connection
	public FifoDistinctCollection<String /*ip*/> serverHistory = new FifoDistinctCollection<>(); // order to fill connection
	public LogDistinctCollection<String /*serviceType*/> clientTypeLogOffset = new LogDistinctCollection<>(); // order to fill connection
	public final Map<String /*ip*/, LBRServerInfo> serverMap = new ConcurrentHashMap<>();
	public final Map<String/*service type: chp, cgw*/,
		Map<String/*ip*/, Pair<String/*client.id that connect to this DNS name*/, LBRMsg>>> clientMap = new ConcurrentHashMap<>(); // all client send to all server

	public Map<String, Map<String, Pair<String, LBRMsg>>> getUnfilledClients(int level) {
		// filter from last state
		Map<String, Map<String, Pair<String, LBRMsg>>> newMap = new ConcurrentHashMap<>();
		clientMap.forEach((serviceType, clientPairMap) -> {
			Map<String, Pair<String, LBRMsg>> typeMap = new ConcurrentHashMap<>();
			clientPairMap.forEach((ip, pair) -> {
				LBRClientInfo client = pair.getRight().clients.get(pair.getLeft());
				if (client.dconfig.maxConnection > level
					&& client.targetMap.size() < level) typeMap.put(ip, pair);
			});
			if (!typeMap.isEmpty()) newMap.put(serviceType, typeMap);
		});
		return newMap;
	}

	private List<Pair<String/*ip*/, LBRClientInfo>> getSortedClientByIp(String serviceType) {
		// sort by ip asc
		return clientMap.get(serviceType).entrySet().parallelStream().map(e -> Pair.of(e.getKey(), e.getValue().getRight().clients.get(e.getValue().getLeft()))).sorted(Comparator.comparing(Pair::getLeft)).collect(Collectors.toList());
	}

	public synchronized void hashRelocate() {
		int maxCapAllQuota = serverMap.values().stream().mapToInt(s -> s.dconfig.maxConnection).sum();
		logger.info("maxCapAllQuota {} {} {}", maxCapAllQuota, serverMap, clientMap);
		if (!clientMap.isEmpty()) {
			Map<String, Integer> serviceDemands = serviceTypeDemands();
			logger.info("serviceDemands {}", serviceDemands);
			int requiredMinConns = serviceDemands.values().stream().mapToInt(Integer::intValue).sum();
			float serviceRate = (maxCapAllQuota > requiredMinConns) ? 1 : (float) maxCapAllQuota / requiredMinConns;
			logger.info("[LBRS:{}] servers {}", endpoint, serverMap.values().stream().map(s -> String.format("%s:%s", s.id, s.targetMap)).collect(Collectors.joining(", ")));

			serverMap.values().forEach(lbrServer -> lbrServer.targetMap.clear()); // clear for rehash
			clientMap.values()
				.forEach(cpm -> cpm.values()
					.forEach(cp-> cp.getRight().clients.values()
						.forEach(client -> client.targetMap.clear())));

			clientTypeLogOffset.forEach(serviceType -> {
				int demandedServiceConns = (int) (serviceRate * serviceDemands.get(serviceType));
				int offset = clientTypeLogOffset.getOrder(serviceType); // -1 if not found, this not gonna happen if add right
				long maxAllowServerConn = Long.min(demandedServiceConns, serverMap.values().stream().mapToLong(lbrServerInfo -> lbrServerInfo.dconfig.maxConnection).sum());
				// cac client cung 1 pod ket noi den 1 DNS se khac offset
				// cac client khac pod co the cung offset
				List<Pair<String, LBRClientInfo>> clients = getSortedClientByIp(serviceType);
				int serverPointer = offset;
				for (int clientPointer = 0; clientPointer < clients.size(); clientPointer++) {
					Pair<String, LBRClientInfo> cPair = clients.get(clientPointer);
					LBRClientInfo selectedLbrClientInfo = cPair.getRight();
					int clientNeedOpen = Integer.min(selectedLbrClientInfo.dconfig.minConnection, serverHistory.size());
					for (; clientNeedOpen > 0 && maxAllowServerConn > 0; clientNeedOpen--, maxAllowServerConn--, serverPointer++, serverPointer %= serverHistory.size()) {
						LBRServerInfo selectedLbrServerInfo = serverMap.get(serverHistory.get(serverPointer));
						String targetIp = selectedLbrServerInfo.endpoint.ip;
						logger.info("Select server ip {}", targetIp);
						selectedLbrClientInfo.targetMap.compute(targetIp, (k, v) -> {
							selectedLbrServerInfo.targetMap.compute(selectedLbrClientInfo.endpoint.ip, (kk, vv) ->
								vv != null ? vv + 1 : 1);
							return v != null ? v + 1 : 1;
						});
					}
					logger.info("[LBRS:{}] Client {}/{}@{} connect to {}", endpoint, serviceType, cPair.getLeft(), selectedLbrClientInfo.endpoint.ip, selectedLbrClientInfo.targetMap);
				}
			});
		}
	}

	public void clientMapReport() {
		logger.info("ClientMap {}", clientMap.values().stream()
			.map(cm -> cm.values().stream()
				.map(cp -> String.format("%s:%s", cp.getLeft(), cp.getRight().clients))
				.collect(Collectors.joining("\n")))
			.collect(Collectors.joining("\n")));
	}

	private Map<String, Integer> serviceTypeDemands() {
		return clientMap.entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey /*serviceType*/,
				s -> s.getValue().values().stream()
						.map(clientPair -> (clientPair.getRight().clients.isEmpty())
							? null
							: clientPair.getRight().clients.get(clientPair.getLeft())
						)
						.filter(Objects::nonNull)
						.mapToInt(client -> {
							logger.info("client config {}", client.dconfig);
							return client.dconfig.minConnection;
						})
						.sum()));
	}

	public synchronized void fillRelocate() {
		// fill connection in order of IP from first min IP
		// but change at least as we can, to avoid relocate all connection -> use pod history
		// process on each client type, client id, -> led to full fill first kind of client but last won't if server not open enough -> scale by max server allow
		int minAllocated = clientMap.values().stream()
			.mapToInt(clientPairMap -> clientPairMap.values().stream()
				.mapToInt(p -> p.getRight().clients.get(p.getLeft()).targetMap.size())
				.min()
				.orElse(0))
			.min()
			.orElse(0);
		Map<String, Integer> serviceTypeAllocated = clientMap.entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey,
				e -> e.getValue().values().stream()
					.mapToInt(p -> p.getRight().clients.get(p.getLeft()).targetMap.size())
					.sum()));
		Map<String, Integer> serviceDemands = serviceTypeDemands();
		Map<String, Map<String, Pair<String, LBRMsg>>> levelClients;
		while (!(levelClients = getUnfilledClients(minAllocated + 1)).isEmpty()) {
			int maxCapAllQuota = serverMap.values().stream().mapToInt(s -> s.dconfig.maxConnection).sum();
			int requiredMinConns = serviceDemands.values().stream().mapToInt(Integer::intValue).sum();
			// in CenterLBR mode, ClientD.mode is Limit and .determineMethod is Centerlized, so required user to define minConn and maxConn
			// minConn for connection serviceRate allocate if over server conns capacity
			// maxConn for filling
			// allocate connection quota by service type:
			float serviceRate = (maxCapAllQuota > requiredMinConns) ? 1 : (float) maxCapAllQuota / requiredMinConns;
			AtomicBoolean levelApplyed = new AtomicBoolean(false);
			for (String serviceType : serverMap.keySet()) {// calculate for each serviceType based on serviceRate to allocate maximum number of connection
				int maxServiceConns = (int) (serviceRate * serviceDemands.get(serviceType));
				AtomicInteger remainConns = new AtomicInteger(maxServiceConns - serviceTypeAllocated.get(serviceType));
				levelClients.get(serviceType).forEach((cIp, clientP) -> {
					if (remainConns.get() > 0) {
						Map<String, Long> dests = clientP.getRight().clients.get(clientP.getLeft()).targetMap;
						String newAssign = assignLowestServer(serviceType, dests);
						if (newAssign != null) {
							dests.compute(newAssign, (k, v) -> v != null ? v + 1 : 1);
							levelApplyed.set(true);
							remainConns.decrementAndGet();
						}
					}
				});
			}
			if (levelApplyed.get())
				minAllocated++; // increase filter level from lowest to highest, until all satisfied or enough connections or receive break signal from over quota
			else break;
		}
	}

	private String assignLowestServer(String serviceType, Map<String, Long> clientDests) {
		int minDest = Integer.MAX_VALUE;
		final LBRServerInfo[] result = {null};
		serverMap.values().stream() // cac server chua ket noi voi client nay
			.filter(lbrServer -> !clientDests.containsKey(lbrServer.endpoint.ip)) // havent connected
			.forEach(validServer -> {
				if (destCountGroupByServer(serviceType).get(validServer.endpoint.ip).get() < minDest)
					result[0] = validServer;
			});
		if (result[0] != null) return result[0].endpoint.ip;
		return null;
	}

	private Map<String, AtomicLong> destCountGroupByServer(String serviceType) {
		Map<String, AtomicLong> serverDestCount = new HashMap<>(); // count no need CCmap
		clientMap.get(serviceType) // map<ip, lbrPodPair>
			.values().forEach(pair -> pair.getRight().clients.get(pair.getLeft()).targetMap
				.forEach((dest, minConn) -> {
					serverDestCount.putIfAbsent(dest, new AtomicLong(0));
					serverDestCount.get(dest).addAndGet(minConn);
				}));
		return serverDestCount;
	}

	public ServiceLBR(Endpoint endpoint) { // service is universal in 1 namespace, not split into deployemnts then have different port like that
		this.endpoint = endpoint;
	}

	public List<LBRServerInfo> getServers() {
		return new CopyOnWriteArrayList<>(serverMap.values());
	}

	public void removeServer(String ip) {
		logger.info("remove server {}", ip);
		serverMap.remove(ip);
		serverHistory.remove(ip);
	}

	public void addServer(LBRServerInfo server) {
		logger.info("Add server with ip {} {}", server.endpoint, server.status());
		addServerPlaceholder(server.endpoint.ip);
		serverMap.putIfAbsent(server.endpoint.ip, server);
	}

	public void addServerPlaceholder(String ip) {
		serverMap.computeIfAbsent(ip, newIp -> {
			serverHistory.add(newIp);
			return null;
		});
	}

	public void updateDNS(Set<String> newResolved) {
		logger.info("New resolved DNS {}", newResolved);
		CollectionUtils.diff(serverMap.keySet(), newResolved).applyIfDiff(new CollectionUtils.Apply1T<>() {
				@Override
				public void onNewApply(String ip) {
					addServerPlaceholder(ip);
				}

				@Override
				public void onDisappearApply(String ip) {
					logger.info("DNS disappaer {}", ip);
					removeServer(ip);
				}

				@Override
				public void onExistedApply(String existedPair) {

				}
			});
	}
}
