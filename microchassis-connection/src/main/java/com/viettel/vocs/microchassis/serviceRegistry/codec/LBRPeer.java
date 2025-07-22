package com.viettel.vocs.microchassis.serviceRegistry.codec;

import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.common.IDfy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tiennn18
 */
public abstract class LBRPeer implements IDfy {
	public String id; // instance scope server.id or client.id
	public Endpoint endpoint;
	public Map<String, Long> targetMap = new ConcurrentHashMap<>();
	public Map<String, Long> currentMap = new ConcurrentHashMap<>();
	protected LBRPeer() { // for LBR server
	}
	protected LBRPeer(String id, Endpoint e){ // for LBR client
		this.id = id;
		this.endpoint = e;
	}

	@Override
	public String getId() {
		return id;
	}
}
