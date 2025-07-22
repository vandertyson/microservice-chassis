package com.viettel.vocs.microchassis.tcp.server;

import com.viettel.vocs.microchassis.codec.handler.tcp.TcpBasedHandler;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import com.viettel.vocs.microchassis.connection.server.ServerChannelInitializer;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tcp.codec.MsgEncoder;
import com.viettel.vocs.microchassis.tcp.codec.TSIDecoder;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author tiennn18
 * General for all sub kind of Msg and server config, with dynamic BCM state
 */
public abstract class TcpBasedServerChannelInitializer<BiDMsg extends Msg, Conf extends ServerConfiguration, SHandler extends TcpBasedHandler> extends ServerChannelInitializer<Conf, SHandler> {
	private static final Logger logger = LogManager.getLogger(TcpBasedServerChannelInitializer.class);
	protected TcpBasedServerChannelInitializer(TcpBasedServer<BiDMsg, Conf, SHandler, ?> server, SHandler handler) {
		super(server, handler);
	}

	protected abstract TSIDecoder<BiDMsg, SHandler> newServerMsgDecoder(TcpBasedServer server, SHandler handler, ServerLimiter limiter);
	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		super.initChannel(ch);
		if(logger.isDebugEnabled()) logger.debug("NEW CHANNEL {}", ch);
		ChannelPipeline pipeline = ch.pipeline();
		pipeline
			.addLast(new MsgEncoder(config.channelConfiguration, config.id))
			.addLast(newServerMsgDecoder((TcpBasedServer) server, handler, limiter));
		if(logger.isDebugEnabled()) logger.debug("AFTER ADDLAST TCPs {}", pipeline);
	}
}
