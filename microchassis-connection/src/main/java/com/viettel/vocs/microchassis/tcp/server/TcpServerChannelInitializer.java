package com.viettel.vocs.microchassis.tcp.server;

import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tcp.codec.MsgSIDecoder;

/**
 * @author tiennn18
 */
public class TcpServerChannelInitializer extends TcpBasedServerChannelInitializer<Msg, ServerConfiguration, TcpHandler> {

	public TcpServerChannelInitializer(TcpServer server, TcpHandler handlers) {
		super(server, handlers);
	}


	/**
	 * server push without timeout and client Decoder will handler pushed msg without count for LB
	 * so MsgDecoder at server do not need to have
	 */

	@Override
	protected MsgSIDecoder newServerMsgDecoder(TcpBasedServer server, TcpHandler handlers, ServerLimiter limiter) {
		return new MsgSIDecoder((TcpServer) server, handlers, limiter);
	}
}
