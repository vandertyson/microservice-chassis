package com.viettel.vocs.microchassis.connection.exception;

import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;

public class NoConnectionForStrategyToSelect extends SelectFailed {
	public NoConnectionForStrategyToSelect(RouteStrategy strategy) {
		super(strategy, "No connection to select");
		this.routeStrategy.reclassifyAll();
	}
}
