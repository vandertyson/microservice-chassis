package com.viettel.vocs.microchassis.connection.exception;

import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Strategy;

public class UpdateStrategyException extends StrategyException {

	public UpdateStrategyException(Strategy strategy, String message) {
		super(strategy, message);

	}
}
