package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.route;

import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.WeightConfigure;

import java.util.Map;

/**
 * @author tiennn18
 * for RouteStrategy
 * some how obtained a destMap, with specified weight, then route in that order
 */
public abstract class Future_WeightedRouteStrategy extends RouteStrategy {
	protected Future_WeightedRouteStrategy(PeerConfig config, WeightConfigure configure, Map<String, Routable> connections) {
		super(config, configure, connections);
	}
	@Override
	public boolean isPressure() {
		return false; // always false, free flow
	}
}
