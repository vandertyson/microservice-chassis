package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.client.ClientStatus;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.exception.SendTimeout;
import com.viettel.vocs.microchassis.connection.exception.StrategyException;
import com.viettel.vocs.microchassis.connection.exception.UpdateStrategyException;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.ManoConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Strategy;
import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.InvalidParameterException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 */

public abstract class UpdateStrategy<Conf extends ClientConfiguration>
	extends Strategy<Site<Conf>> {
	protected static final Logger logger = LogManager.getLogger(UpdateStrategy.class);

	protected final String mainSiteHostName;

	public ClientStatus getStatuses(ClientStatus status) {
		destMap.values().forEach(site -> site.fetchStatus(status));
		status.setResolvedIP(
			destMap.entrySet().stream()
				.collect(Collectors.toMap(
					Map.Entry::getKey,
					e -> e.getValue().getConnections().stream()
						.map(Routable::ip)
						.collect(Collectors.toList()))));
		return status;
	}

	/**
	 * standard for multiple hostname
	 * handle key created by business logic, prefer (not required) stateful routing for key.
	 * ex: I 5xU T of 1 key send strictly to only one microservice instance
	 * distribute with Rendezvous Weighted Random Hash (WRH)
	 * if microservice instance down, key will be migrated to other instance by reassign (WRH)
	 *
	 * @return select()
	 * @throws StrategyException
	 */

	/**
	 * Routable group
	 */
	protected boolean enable;

	public boolean isEnable() {
		return enable;
	}

	public synchronized void disable(boolean closeConnOfMainSite) { // disable phai bat synchronized de clear resource va set enable false
		// clear sites
		///2
		enable = false;
		///1
		// close all connections except mainSite
		destMap.values().stream()
			.filter(site -> closeConnOfMainSite || !Objects.equals(site.getHostName(), mainSiteHostName))
			.forEach(site -> site.getConnections().forEach(conn -> site.deregister(conn, false)));
		logger.info("[Client closed successfully]{listConn={}}", mainSite.getConnections().size());

		destMap.keySet().stream()
			.filter(k -> !Objects.equals(k, mainSiteHostName))
			.peek(ManoConfigure::removeConfig).forEach(destMap::remove);
	}

	public ManoConfigure enable(NettyClient<?, ?, Conf, ?> mainSite) {
		// re-generate sites
		destMap.putIfAbsent(mainSiteHostName, mainSite);
		///1
		getAllSiteName().stream()
			.filter(name -> !Objects.equals(name, mainSiteHostName))
			.forEach(siteName -> destMap.computeIfAbsent(siteName, updateSiteHostName -> {
				Site<Conf> newSite = new Site<>(mainSite, updateSiteHostName);
				mainSite.initDNS();
				return newSite;
			}));

		///2
		enable = true; // set cuoi cung
		// create both blue and green
		return ManoConfigure.getConfig(mainSiteHostName);

	}


	protected UpdateStrategy(Site<Conf> mainSite) {
		this.mainSite = mainSite;
		this.mainSiteHostName = mainSite.getHostName();
		destMap.putIfAbsent(mainSiteHostName, this.mainSite);
//		baseServiceName = chooseBaseServiceName(getAllSiteName()); // created by nettyclient with main serviceName
	}

	/**
	 * tuy theo Strategy update bruteforce generate tat ca site name cho viec update
	 *
	 * @return valid DNS names
	 */
	public abstract List<String> getAllSiteName();

	public abstract List<String> otherSiteNames(String thisSiteName);

	public static String chooseBaseServiceName(List<String> serviceNames) {
		if (!serviceNames.isEmpty()) {
			return serviceNames.stream()
				.min(Comparator.comparing(String::length))
				.get();
		}
		throw new InvalidParameterException("ServiceName(s) mismatch to find baseServiceName:" + serviceNames + "");
	}

	protected abstract UpdateSite anySite() throws UpdateStrategyException; // immediately return

	/**
	 * select between sites
	 *
	 * @param key
	 * @return
	 */
	protected abstract UpdateSite selectSite(String key) throws UpdateStrategyException; // immediately return

	public Routable select(TimeUtils.NanoBeacon startBeacon) throws StrategyException, SendTimeout {
		UpdateSite execSite = enable
			? anySite() // immediately return
			: mainSite;
		return execSite.select(startBeacon);
	}

	public Routable select(TimeUtils.NanoBeacon startBeacon, String key) throws StrategyException, SendTimeout {
		UpdateSite execSite = enable
			? selectSite(key) // immediately return
			: mainSite;
		return execSite.select(startBeacon);
	}

	public final Site<Conf> mainSite;

	public Map<String, Site<Conf>> getSites() {
		return destMap;
	}

	public Site<Conf> getSite(String serviceName) {
		return destMap.get(serviceName);
	}

	public Set<Routable> getAllSiteConnections() {
		return destMap.values().stream()
			.map(Site::getConnections)
			.flatMap(Set::stream)
			.collect(Collectors.toSet());
	}

	public boolean isConnected() { // true if any connection in any client connected
		return destMap.values().stream().anyMatch(Site::routeStrategyConnected);
	}


	public void gracefulCloseAllRelevantConns(Channel channel) {
		Endpoint closeEndpoint = Endpoint.remote(channel);
		destMap.values().forEach(site -> site.getConnections().stream()
				.filter(cw ->
					closeEndpoint != null && Endpoint.equalsWithoutDns(cw.getEndpoint(), closeEndpoint) // close by endpoint for all conn on that server
						|| cw.getChannel() != null && Objects.equals(cw.getChannel().id(), channel.id()) // close specific ctx by id
				).forEach(cw -> ChassisThreadManager.createConnPool.execute(() -> site.deregister(cw, false))));
	}

	public String getConnectionStatString() {
		ManoConfigure config = ManoConfigure.getConfig(mainSiteHostName);
		return String.format("Client connections stat: id=%s, Sites=%s", mainSite.getId(),
			String.format("[%s, update=%s, weight=%s]",
				enable
					? "\n" + destMap.values().stream().map(Site::getConnectionInfo).collect(Collectors.joining(",\n")) + "\n"
					: mainSite.getConnectionInfo()
				, this, config != null ? config.toString() : null));
	}

	public String getConnectionDetailString() {
		return enable
			? destMap.values().stream().map(Site::getConnectionDebug).collect(Collectors.joining("\n"))
			: mainSite.getConnectionDebug();
	}

	public Map<String, Long> countConnectedMap() {
		return destMap.entrySet().stream() // always have mainSite
			.collect(Collectors.toMap(Map.Entry::getKey, e
				-> e.getValue().countEnabled()));
	}


	@Override
	public void intervalRefresh() {
		/**
		 * Weight map is set statically, alter from Mano, then overwrite by weight. Read instantly, so no need to be refreshed
		 */
	}
}
