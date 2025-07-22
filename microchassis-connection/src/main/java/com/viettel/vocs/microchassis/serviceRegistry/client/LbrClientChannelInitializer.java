package com.viettel.vocs.microchassis.serviceRegistry.client;


import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRMsg;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LbrCIDecoder;
import com.viettel.vocs.microchassis.serviceRegistry.codec.handler.LbrHandler;
import com.viettel.vocs.microchassis.tcp.client.TcpBasedClientChannelInitializer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tiennn18
 */
public class LbrClientChannelInitializer extends TcpBasedClientChannelInitializer<LBRMsg, LBRClientConfiguration, LbrHandler> {

	public LbrClientChannelInitializer(LbrClientConnection conn, MsgCounter msgCounter) {
		super(conn, msgCounter);
	}

	@Override
	protected LbrCIDecoder newClientMsgDecoder(EventHandler EventHandler, LbrHandler handlers, Map<String, CompletableFuture> syncPromiseMap, ClientConfiguration config, AtomicReference<PeerCounter> counterRef, AtomicReference<ConnectionMode> connModeRef, MsgCounter msgCounter) {
		return new LbrCIDecoder(EventHandler, handlers, syncPromiseMap, config, counterRef, connModeRef, msgCounter);
	}
}