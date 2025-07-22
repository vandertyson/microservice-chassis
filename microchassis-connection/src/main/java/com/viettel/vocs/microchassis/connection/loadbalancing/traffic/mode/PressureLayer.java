package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BackConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerConnectionState;

/**
 * @author tiennn18
 */
interface PressureLayer {
	void pressWithCache(BackConfigure current, PeerConnectionState state); // return TPS allow after press on 1 trigger only, any else will return immediately to read from config.getTps
	void press(BackConfigure current, PeerConnectionState state); // return TPS allow after press

	void pressNext(BackConfigure current, PeerConnectionState state); // called inside press for next chain (recursive effect)
}
