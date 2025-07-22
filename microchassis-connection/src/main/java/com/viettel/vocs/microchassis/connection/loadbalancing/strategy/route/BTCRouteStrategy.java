package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.route;

import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BTC;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.BTCConnectionMode;

import java.util.Map;

/**
 * @author tiennn18
 * QoS feature at TCP context
 * Thuat toan: xem draw.io page State QoS
 */

public class BTCRouteStrategy extends QoSRouteStrategy {

	public BTCRouteStrategy(PeerConfig config, Map<String, Routable> connections) {
		super(config, new BTC(), connections);
	}

	public BTCRouteStrategy(PeerConfig config, StrategyConfigure configure, Map<String, Routable> connections) {
		super(config, configure, connections);
	}


	@Override
	public BTCConnectionMode newReadyMode(String channelId) {
		return new BTCConnectionMode(channelId, (BTC) routeConfig);
	}
}
