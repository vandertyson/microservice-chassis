package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BackConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnState;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.Pressure;

public class StateSnapshot {
	public int recentCC;
	public int configuredCC;
	public float ccUtilization;
	public boolean enableControl;
	public int peerLoadB10k;
	public int curTPS;
	public int configuredTPS;
	public float tpsUtilization;

	public StateSnapshot(BackConfigure conf, ConnState state, Pressure pressureSource) {
		this.recentCC = state.recentCC();
		this.configuredCC = conf.concurrent.get();
		ccUtilization = (float) recentCC / configuredCC;
		peerLoadB10k = pressureSource.peerLoadBase10k.get();
		enableControl = pressureSource.isEnableControl(peerLoadB10k);
		curTPS = state.getAvgSend();
		configuredTPS = conf.getTPS();
		tpsUtilization = (float) curTPS / configuredTPS;
	}
}
