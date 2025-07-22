/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.tcp.client;


import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.client.ChannelMonitor;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.tcp.codec.Msg;

public class TcpClient extends TcpBasedClient<Msg, ClientConfiguration, TcpHandler> {
	public TcpClient(ClientConfiguration config) {
		super(config);
	}


//	public TcpClient(Properties properites) {
//		super(new ClientConfiguration(properites));
//	}
	@Override
	public Msg createReq(byte[] data, String path, String msgId) {
		Msg msg = new Msg(bytebufAllocator, path, msgId);
		msg.writeFrom(data);
		return msg;
	}

	public Msg createReq(byte[] data, String path) {
		return createReq(data, path, Msg.newMsgId());
	}

	@Override
	public final TcpClientConnection createConnection(Site<ClientConfiguration> ownerSite, Endpoint endpoint, EventHandler eventHandler, boolean monitor) {
		return createConnection(ownerSite, endpoint, handler, eventHandler, monitor);
	}

	@Override
	public final TcpClientConnection createConnection(Site<ClientConfiguration> ownerSite, Endpoint endpoint, TcpHandler handlers, EventHandler eventHandler, boolean monitor) {
		TcpClientConnection conn = new TcpClientConnection(config, eventHandler, ownerSite, endpoint, handlers, bootstrap, ownerSite.getRouteStrategyRef());
		if(!conn.isClosed()){  // not to be closed after register
			// => allow to add new connection
			if (monitor)	ChannelMonitor.getInstance().registerMonitorObject(conn);
//			logger.info(conn.isConnected()
//					? "Created connection to {} (channelActive={} connectedBeforeAdd={}/conPerIP={})"
//					: "Connection to {} error, queued for monitor retry (channelActive={} connected={} conPerIP={})",
//				endpoint, conn.isConnected(), countConnected(endpoint), config.connectionPerIP);// server drop make channel die /* khong bi server deny */
			return conn;
		} else return null;
	}
}
