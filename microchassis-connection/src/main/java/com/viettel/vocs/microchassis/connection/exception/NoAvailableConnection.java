package com.viettel.vocs.microchassis.connection.exception;

import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;

public class NoAvailableConnection extends RouteStrategyException {
    public NoAvailableConnection(RouteStrategy r, String message) {
        super(r, message);
        ConnectionManager.strategyDropStack();
    }
}
