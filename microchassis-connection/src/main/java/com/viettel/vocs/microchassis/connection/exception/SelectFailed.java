package com.viettel.vocs.microchassis.connection.exception;

import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;

public class SelectFailed extends RouteStrategyException {
	public SelectFailed(RouteStrategy strategy, String message) {
		super(strategy, "Select failed. " + message);
	}

	public SelectFailed(RouteStrategy strategy) {
		super(strategy, "Select failed");
	}
}
