package com.viettel.vocs.microchassis.serviceRegistry.client;


import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.RequestTimeoutMonitor;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRMsg;
import com.viettel.vocs.microchassis.serviceRegistry.codec.handler.LbrHandler;
import com.viettel.vocs.microchassis.tcp.client.TcpBasedClientConnection;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tiennn18
 */
public class LbrClientConnection extends TcpBasedClientConnection<LBRMsg, LBRClientConfiguration, LbrHandler> {
	private static final Logger logger = LogManager.getLogger(LbrClientConnection.class);


	@Override
	protected void newChannelReady() {
		if (config.sendTimeoutMs > 0) RequestTimeoutMonitor.registerService(config.id, this, getHandler()); // registerService timeout sau khi da set counter, set nettyChannel!=nul
	}

	public LbrClientConnection(LBRClientConfiguration config, EventHandler eventHandler, Site<LBRClientConfiguration> ownerSite, Endpoint endpoint, LbrHandler handlers, Bootstrap bootstrap, AtomicReference<RouteStrategy> routeStrategyRef) {
		super(config, eventHandler, ownerSite, endpoint, handlers, bootstrap, routeStrategyRef);
		logger.info("Creating LBR connection. clientId={}, endpoint={}",config.id,endpointRepresent);
		init(); // call ping here -> connect() // registered
	}
	@Override
	protected LbrClientChannelInitializer newClientInitializer(MsgCounter msgCounter) {
		return new LbrClientChannelInitializer(this, msgCounter);
	}

	@Override
	protected LBRMsg makeCreateMsg(ByteBufAllocator allocator) {
		return LBRMsg.clientMakeCreate(allocator);
	}

	@Override
	protected LBRMsg makePingMsg(ByteBufAllocator allocator) {
		return LBRMsg.clientMakePing(allocator);
	}
}
