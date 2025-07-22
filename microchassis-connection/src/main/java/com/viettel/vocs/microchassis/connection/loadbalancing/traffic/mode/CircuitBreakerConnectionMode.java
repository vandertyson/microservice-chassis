package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

public class CircuitBreakerConnectionMode extends StaticLimitedFrontPressureConnectionMode {
	public CircuitBreakerConnectionMode(StepConfigure stepper) {
		super(stepper);
	}

	@Override
	public int getConfiguredCC() {
		return getTps(); // allow each step in 1 window
	}

}
