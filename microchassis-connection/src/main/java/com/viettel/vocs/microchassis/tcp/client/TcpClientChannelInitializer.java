package com.viettel.vocs.microchassis.tcp.client;


import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import com.viettel.vocs.microchassis.tcp.codec.MsgCIDecoder;
import com.viettel.vocs.microchassis.tcp.codec.TCIDecoder;
import com.viettel.vocs.microchassis.tcp.codec.Msg;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class TcpClientChannelInitializer extends TcpBasedClientChannelInitializer<Msg, ClientConfiguration, TcpHandler> {

	public TcpClientChannelInitializer(TcpClientConnection conn, MsgCounter msgCounter) {
		super(conn, msgCounter);
	}

	@Override
	protected TCIDecoder<Msg, TcpHandler> newClientMsgDecoder(EventHandler eventHandler, TcpHandler handlers, Map<String, CompletableFuture> syncPromiseMap, ClientConfiguration config, AtomicReference<PeerCounter> counterRef, AtomicReference<ConnectionMode> connModeRef, MsgCounter msgCounter) {
		return new MsgCIDecoder(eventHandler, handlers, syncPromiseMap, config, counterRef, connModeRef, msgCounter);
	}
}
