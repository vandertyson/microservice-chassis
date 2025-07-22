package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.route;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.RCP;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CachedPoolRouteStrategy extends PooledRouteStrategy {
	private final RCP rrCachedPooledConfig;
	/**
	 * RoundRobin based
	 * create if cannot select conn
	 * creation respects routeStrategy.maxConnectionPerIp -> mesh.maxConnection and site.maxCreateConn
	 */
	private final Site ownerSite; // any site is ok, it will call to ownerSite
	protected ConcurrentHashMap<String, Routable> freeConns; // readiers/destMap are whole pool

	@Override
	public boolean isConnected() {
		if (countAvailDest() == 0) createMoreConn();
		return true; // always true, trigger create when no connection
	}

	public CachedPoolRouteStrategy(PeerConfig config, StrategyConfigure configure, Map<String, Routable> connections, Site ownerSite) {
		super(config, configure, connections);
		rrCachedPooledConfig = (RCP) routeConfig;
		this.ownerSite = ownerSite;
	}


	/**
	 * create to use then cached for timeout then be killed
	 */
	public void createMoreConn() {
//		logger.debug("Trigger create conn: {}", report());
		if (TimeUtils.isTimeoutMili(ownerSite.getLastSeedCreateConn(), rrCachedPooledConfig.sleepBetweenConnectMs)
			&& (routeConfig.maxConnectionPerIp == 0 || routeConfig.maxConnectionPerIp > destMap.size())) {
//			logger.debug("Joined create conn: {}", report());
			ownerSite.seedCreateConnJobs(1); // only apply for full required conns
		}
	}

	public void closeConn(Routable conn) { // if close less than connPerIP -> recreate to later use
		if (destMap.containsKey(conn.getId())) conn.deregister();
	}

	public Routable claimConn(String connId) {
		return freeConns.remove(connId);
	}
}
