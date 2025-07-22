package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

/**
 * @author tiennn18
 */

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.FrontConfigure;

import java.util.concurrent.TimeUnit;

/**
 * kinda incoming TPS pressure, read from startup config
 */

public class FrontPressureConnectionMode extends DynamicFrontPressureConnectionMode {
	public FrontPressureConnectionMode(){
		super();
	} // for server

	public FrontPressureConnectionMode(FrontConfigure config){ super(config); } // for clients


	@Override
	public String report() {
		return String.format("%d/%ds/%dh/%dms", config.getTPS(), config.operateMaxAvgTPS.get(), config.operateMaxPeakTPS.get(), TimeUnit.NANOSECONDS.toMillis(config.getWindow()));
	}

	@Override
	public boolean isUnderPressure() {
		return false; // always false for front
	}
}

