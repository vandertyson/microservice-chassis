/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.server;

import com.viettel.vocs.microchassis.codec.handler.tcp.ServerHandler;
import com.viettel.vocs.microchassis.connection.NettyChannelInitializer;
import com.viettel.vocs.microchassis.connection.client.ChannelAttribute;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicReference;

public abstract class ServerChannelInitializer<Conf extends ServerConfiguration, SHandler extends ServerHandler>
	extends NettyChannelInitializer {
	private static final Logger logger = LogManager.getLogger(ServerChannelInitializer.class);
	protected final ServerLimiter limiter; // null safe
	protected final Conf config;
	protected final SHandler handler;
	protected final NettyServer<Conf, SHandler, ?> server;

	protected ServerChannelInitializer(NettyServer<Conf, SHandler, ?> server, SHandler handler) {
		super(server.getEventHandler(), server.getMsgCounter());
		this.server = server;
		this.config = server.getConfig();
		this.handler = handler;
		this.limiter = server.getLimiter();
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception { // khi 1 conn moi ket noi den server
		super.initChannel(ch);
		ch.attr(ServerChannelAttribute.serverRef).set(server); // for msgdecoder chain handle and accept allowed connections
		if(logger.isDebugEnabled()) logger.info("New channel connected to server {}", config.id);
		// init resouces bind with channel, external usage need to use by getter of initializer
		ch.attr(ChannelAttribute.counter).set(new AtomicReference<>(server.getCounter()));

		if(server.pause.get()) {
			logger.info("Server {} deny new channel {} connect from client by stopped ", config.id, ch);
			ch.close();
			return;
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("ServerInitializer exceptionCaught {} cause {}", ctx.channel(), cause);
		logger.error(cause, cause);
		super.exceptionCaught(ctx, cause);
	}
}
