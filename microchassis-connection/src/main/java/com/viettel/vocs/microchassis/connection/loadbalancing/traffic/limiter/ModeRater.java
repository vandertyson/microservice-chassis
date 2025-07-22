package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter;

import com.viettel.vocs.common.log.limiter.SemiAutoSlideRateLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.WarmupConnectionMode;

import java.util.Objects;


public class ModeRater extends SemiAutoSlideRateLimiter {
	private ConnectionMode mode;

	public int warmingUpTPS() {
		return mode instanceof WarmupConnectionMode ? mode.getTps() : 0;
	}
	@Override
	public int getTps(){
		return mode.getTps();
	}

	public long getWindow(){
		return mode.getWindow();
	}
	@Override
	public long getMinWait() {
		return mode.getMinWait();
	}
	public void setMode(ConnectionMode newMode) {
		this.mode = Objects.requireNonNull(newMode);
	}

	public ModeRater(ConnectionMode initMode) {
		setMode(initMode);
	}
	public ConnectionMode getMode() {
		return mode;
	}

}
