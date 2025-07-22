package com.viettel.vocs.microchassis.connection.exception;

import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;

public class ServiceNotAvailable extends NoAvailableConnection {

	public ServiceNotAvailable(RouteStrategy r, String message) {
		super(r, message);
	}
}
