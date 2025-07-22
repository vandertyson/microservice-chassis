package com.viettel.vocs.microchassis.connection.server;

import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.microchassis.base.Endpoint;

import java.util.Map;

public interface ServerAPI extends IDfy {
	Map<String, Long> countIps();
	String getId();
	Map<Endpoint, Long> countEndpoints();

	Map<NettyServer.ServerLinkAddress, Integer /*count*/>  getConnectedEndpointPair();
}
