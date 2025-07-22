package com.viettel.vocs.microchassis.connection.loadbalancing.negotiator;

import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRClientInfo;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRServerInfo;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;

import java.util.Map;

/**
 * @author tiennn18
 */
public interface LBNegotiator {
	// TODO dev cai nay di
	/**
	 *  moi pod co nhieu client, can register client de monitor LB negtor
	 */

	void updateClients(Map<String, LBRClientInfo> clients); // update all at once from center
//	void updateTcpClients(Map<String, TcpLBRClient> clients); // update all at once from center
//	void updateHttpClients(Map<String, HttpLBRClient> clients); // update all at once from center
	void updateClient(Site<?> site); // client trigger update change
//	void updateTcpServers(Map<String, TcpLBRServer> servers); // update all at once from center
//	void updateHttpServers(Map<String, HttpLBRServer> servers); // update all at once from center
	void updateServers(Map<String, LBRServerInfo> servers); // update all at once from center
	void updateServer(NettyServer server); // client trigger update change
	void deregisterServer(String id);
	void deregisterClient(String id);
	void registerServer(NettyServer server);
	void registerClient(Site<?> client);
}
