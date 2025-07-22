package com.viettel.vocs.microchassis.serviceRegistry.server;

import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRMsg;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LbrSIDecoder;
import com.viettel.vocs.microchassis.serviceRegistry.codec.handler.LbrHandler;
import com.viettel.vocs.microchassis.tcp.codec.TSIDecoder;
import com.viettel.vocs.microchassis.tcp.server.TcpBasedServer;
import com.viettel.vocs.microchassis.tcp.server.TcpBasedServerChannelInitializer;

/**
 * @author tiennn18
 */
public class LbrServerChannelInitializer extends TcpBasedServerChannelInitializer<LBRMsg, LbrServerConfiguration, LbrHandler> {

	public LbrServerChannelInitializer(LbrServer server, LbrHandler handlers) {
		super(server, handlers);
	}


	@Override
	protected TSIDecoder<LBRMsg, LbrHandler> newServerMsgDecoder(TcpBasedServer server, LbrHandler handler, ServerLimiter limiter) {
		return new LbrSIDecoder((LbrServer) server, handler, limiter);
	}
}
