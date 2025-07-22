/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.tcp.client;


import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.RequestTimeoutMonitor;
import com.viettel.vocs.microchassis.connection.client.ClientChannelInitializer;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;

import java.util.concurrent.atomic.AtomicReference;


/**
 * @author vttek
 */
public class TcpClientConnection extends TcpBasedClientConnection<Msg, ClientConfiguration, TcpHandler> {
	public TcpClientConnection(ClientConfiguration config, EventHandler eventHandler, Site<ClientConfiguration> ownerSite, Endpoint endpoint, TcpHandler handlers, Bootstrap bootstrap, AtomicReference<RouteStrategy> routeStrategyRef) {
		super(config, eventHandler, ownerSite, endpoint, handlers, bootstrap, routeStrategyRef);
	}

	@Override
	protected ClientChannelInitializer<ClientConfiguration, TcpHandler> newClientInitializer(MsgCounter msgCounter) {
		return new TcpClientChannelInitializer(this, msgCounter);
	}

	@Override
	protected void newChannelReady() {
		if (config.sendTimeoutMs > 0) RequestTimeoutMonitor.registerService(config.id, this, getHandler()); // registerService timeout sau khi da set counter, set nettyChannel!=nul
	}

	@Override
	protected Msg makeCreateMsg(ByteBufAllocator allocator) {
		return Msg.makeCreate(allocator);
	}

	@Override
	protected Msg makePingMsg(ByteBufAllocator allocator) {
		return Msg.makePing(allocator);
	}
}
