package com.viettel.vocs.microchassis.connection.loadbalancing.negotiator;

import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.serviceRegistry.client.LBRClientConfiguration;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRClientInfo;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRServerInfo;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;

import java.util.Map;

/**
 * @author tiennn18
 */
public class StandaloneLBR extends LBRAdapter {
	@Override
	public StandaloneLBR waitInit() {
		// do nothing
		return this;
	}

	public StandaloneLBR(LBRClientConfiguration config){
		super(config);
	}

	@Override
	public void updateClients(Map<String, LBRClientInfo> clients) {

	}

	@Override
	public void updateClient(Site<?> site) {

	}


	@Override
	public void updateServers(Map<String, LBRServerInfo> servers) {

	}

	@Override
	public void updateServer(NettyServer server) {

	}


	@Override
	public void deregisterServer(String id) {

	}

	@Override
	public void deregisterClient(String id) {

	}

	@Override
	public void registerServer(NettyServer server) {

	}

	@Override
	public void registerClient(Site<?> client) {

	}
}
