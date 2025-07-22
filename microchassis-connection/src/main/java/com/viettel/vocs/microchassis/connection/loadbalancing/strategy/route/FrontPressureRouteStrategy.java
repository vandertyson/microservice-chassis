package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.route;

import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.FrontConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.FrontPressureConnectionMode;

import java.util.Map;

public abstract class FrontPressureRouteStrategy extends RouteStrategy {

	protected FrontPressureRouteStrategy(PeerConfig config, StrategyConfigure configure, Map<String, Routable> connections) {
		super(config, configure, connections);
	}

	@Override
	public boolean isPressure() {
		return false; // always false, free flow
	}

	@Override
	public ConnectionMode newReadyMode(String channelId) {
		return new FrontPressureConnectionMode((FrontConfigure) routeConfig);
	}
}
