package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.route;

import com.viettel.vocs.microchassis.connection.client.ClientChannelAttribute;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BCC;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.BCCConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.BackPressureConnectionMode;

import java.util.Map;
import java.util.Objects;

/**
 * @author tiennn18
 */
public class CCRouteStrategy extends QoSRouteStrategy {

	public CCRouteStrategy(PeerConfig config, Map<String, Routable> connections) {
		super(config, new BCC(), connections);
	}

	public CCRouteStrategy(PeerConfig config, StrategyConfigure configure, Map<String, Routable> connections) {
		super(config, configure, connections);
	}

	@Override
	public BCCConnectionMode newReadyMode(String channelId) {
		return new BCCConnectionMode(channelId, (BCC) routeConfig);
	}
}
