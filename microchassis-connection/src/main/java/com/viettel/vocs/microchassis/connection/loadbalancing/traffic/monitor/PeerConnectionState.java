package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PeerConnectionState extends BasicConnectionState {
	/**
	 * at client we control cc by mapSent
	 */
	protected final long ccDurationNs;
	protected final ConcurrentHashMap<String /* msgId */, Pair<Long  /*send time, 0 if not monitor timeout*/, Long /*timeout duration*/>> mapSent = new ConcurrentHashMap<>(ChassisConfig.RouteConfig.RequestTimeoutConfig.INITIAL_MAP_REQUEST_TIMEOUT_SIZE.getInt());
	public PeerConnectionState(StrategyConfigure configure){
		this.ccDurationNs = configure != null ? TimeUnit.MILLISECONDS.toNanos(configure.ccDurationMs) : 0;
	}
	public PeerConnectionState(){
		this.ccDurationNs = 0;
	}
	@Override
	public int curCC() {
		return mapSent.size();
	}
	public int recentCC(){
		return ccDurationNs > 0 ? recentCC(ccDurationNs) : curCC();
	}
	@Override
	public int recentCC(long ns) {
		long startRange = System.nanoTime() - ccDurationNs;
		return (int) mapSent.values().stream().filter(p -> p.getLeft() > 0 && startRange < p.getLeft()).count(); // map size is int so it safe to cast int of map filter count
	}

	public ConcurrentHashMap<String, Pair<Long,Long>> getMapSent() {
		return mapSent;
	}
	@Override
	public float getErrorRate() {
		return (float) avgError.get() / getMonitoringReqCount();
	}
}