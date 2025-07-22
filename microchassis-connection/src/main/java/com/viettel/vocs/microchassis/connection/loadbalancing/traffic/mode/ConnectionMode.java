package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerConnectionState;

public interface ConnectionMode {
	boolean isEnd();
	int getConfiguredCC();
	void close();

	static PeerConnectionState newState(StrategyConfigure configure){
		return new PeerConnectionState(configure);
	}

	String report();


	StrategyConfigure getConfig();
	long getWindow();
	long getMinWait();
	int getTps();

}
