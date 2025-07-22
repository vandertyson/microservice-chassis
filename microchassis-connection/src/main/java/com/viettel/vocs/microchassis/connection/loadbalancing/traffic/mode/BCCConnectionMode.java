package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BCC;

public class BCCConnectionMode extends BackPressureConnectionMode {
	public BCCConnectionMode(String channelId, BCC config) {
		super(new BCCPressure(channelId, config));
	}
	public boolean isUnderPressure() {
		BCCPressure bccPressure = (BCCPressure) pressureSource;
		switch (bccPressure.getCurrentFsmState()){
			case 1:
				return bccPressure.estimateLoadStatelessOnline(); //isHighload
			case 2:
			case 3:
			default:
				return false;
		}
	}
	@Override
	public String report() {
		return pressureSource.report(getBackConfigure(), state);
	}
}
