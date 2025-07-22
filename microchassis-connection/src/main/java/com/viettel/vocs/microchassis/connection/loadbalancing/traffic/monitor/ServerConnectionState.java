//package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;
//
//import java.util.concurrent.ConcurrentHashMap;
//
//public class ServerConnectionState extends BasicConnectionState {
//	/**
//	 * at server, response is not guaranteed, so we not control cc -> errorRate = 0% always
//	 * like TCP, it doesn't know when the client received response from server sent at what time
//	 */
//	public int curCC() {
//		return 0;
//	}
//
//	@Override
//	public float getErrorRate() {
//		return 0;
//	}
//
//	public ConcurrentHashMap<String, Long> getMapSent() {
//		return null;
//	}
//}
