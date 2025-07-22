package com.viettel.vocs.microchassis.connection.exception;

import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;

public class RouteStrategyException extends StrategyException {
	protected transient RouteStrategy routeStrategy;

	public RouteStrategyException(RouteStrategy strategy, String message) {
		super(strategy, message);
		routeStrategy = strategy;
	}
}
