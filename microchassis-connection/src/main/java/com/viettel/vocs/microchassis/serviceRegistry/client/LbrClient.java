package com.viettel.vocs.microchassis.serviceRegistry.client;

import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.client.ChannelMonitor;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRMsg;
import com.viettel.vocs.microchassis.serviceRegistry.codec.handler.LbrHandler;
import com.viettel.vocs.microchassis.tcp.client.TcpBasedClient;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author tiennn18
 */
public class LbrClient extends TcpBasedClient<LBRMsg, LBRClientConfiguration, LbrHandler> {
	public AtomicBoolean sessionConnected = new AtomicBoolean(false);
	public LbrClient(LBRClientConfiguration config) {
		super(config);
		eventHandler
			.addEventHandler(ContextEvent.EventType.CHANNEL_ACTIVE, (Consumer<ContextEvent>) event -> sessionConnected.set(true))
			.addEventHandler(ContextEvent.EventType.CHANNEL_INACTIVE, (Consumer<ContextEvent>) event ->{
			if(!routeStrategyConnected()) sessionConnected.set(false);
		});
	}

	@Override
	public LBRMsg createReq(byte[] data, String path, String msgId) {
		return null; // lbr client chi can copy tu representMsg nen ko can craeteReq
	}

	@Override
	public LBRMsg createReq(byte[] data, String path) {
		return null; // lbr client chi can copy tu representMsg nen ko can craeteReq
	}

	@Override
	public LbrClientConnection createConnection(Site<LBRClientConfiguration> ownerSite, Endpoint endpoint, EventHandler eventHandler, boolean monitor) {
		return createConnection(ownerSite, endpoint, handler, eventHandler, monitor);
	}


//	public LbrClient(Properties properites) {
//		super(new LBRClientConfiguration(properites));
//	}

	@Override
	public final LbrClientConnection
	createConnection(Site<LBRClientConfiguration> ownerSite, Endpoint endpoint, LbrHandler handlers, EventHandler eventHandler, boolean monitor) {
		LbrClientConnection conn = new LbrClientConnection(config, eventHandler, ownerSite, endpoint, handlers, bootstrap, ownerSite.getRouteStrategyRef());
		if(!conn.isClosed()){  // not to be closed after register
			// => allow to add new connection
			if (monitor) ChannelMonitor.getInstance().registerMonitorObject(conn);
//			logger.info(conn.isConnected()
//					? "Created connection to {} (channelActive={} connectedBeforeAdd={}/conPerIP={})"
//					: "Connection to {} error, queued for monitor retry (channelActive={} connected={} conPerIP={})",
//				endpoint, conn.isConnected(), countConnected(endpoint), config.connectionPerIP);// server drop make channel die /* khong bi server deny */
			return conn;
		} else return null;
	}

}
