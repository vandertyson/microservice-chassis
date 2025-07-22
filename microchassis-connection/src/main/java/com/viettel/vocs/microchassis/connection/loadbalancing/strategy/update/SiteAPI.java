package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tiennn18
 */
public interface SiteAPI extends IDfy {
	AtomicReference<RouteStrategy> getRouteStrategyRef();

	Map<Endpoint, Long> countEndpoints();
	String getId();
	Map<Endpoint /*IP*/, Long> countConnectedEndpoints();

	Map<String /*IP*/, Long> countConnectedIps();

	Set<Endpoint> getConnectedEndpoints();

	Set<Routable> getConnections();

	Set<String> resolveDNS();

	List<Routable> getNotDeleteMarker();

//	List<Endpoint> selectEndpoints();

	Map<String, Routable> getOpenConnections();
}
