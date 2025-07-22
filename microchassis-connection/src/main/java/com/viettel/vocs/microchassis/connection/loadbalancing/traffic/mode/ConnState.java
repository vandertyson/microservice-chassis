package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.ConcurrentHashMap;

public interface ConnState {
	int curCC();
	int recentCC();
	int recentCC(long ns);
	int getAvgSend();
	int getAvgSuccess();
	float getErrorRate();
	int getAvgError();
	void setWindow(long window);
	void stackSuccess();
	void stackError();
	int getMonitoringReqCount();
	void stack();
	ConcurrentHashMap<String, Pair<Long,Long>> getMapSent();
}
