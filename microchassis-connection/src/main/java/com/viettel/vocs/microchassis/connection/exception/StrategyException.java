package com.viettel.vocs.microchassis.connection.exception;

import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Strategy;



public class StrategyException extends Exception {
    protected final Strategy strategy;
    public StrategyException(Strategy strategy, String message) {
        super(message);
        this.strategy = strategy;
    }
}

