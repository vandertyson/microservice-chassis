package com.viettel.vocs.microchassis.connection.client;

import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.handler.ClientHandler;
import com.viettel.vocs.microchassis.connection.NettyChannelInitializer;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ClientLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicReference;


/**
 * base initializer to hold mgr/control components for client, use for both http, tcp
 *
 * @param <Conf>
 */
public abstract class ClientChannelInitializer<Conf extends ClientConfiguration, CHandler extends ClientHandler>
	extends NettyChannelInitializer {
	private static final Logger logger = LogManager.getLogger(ClientChannelInitializer.class);
	@Getter protected final CHandler handler;
	protected final Conf config;
	private final AtomicReference<RouteStrategy> routeStrategyRef;
	protected final ClientConnection<?,?,Conf, CHandler> ownerConn;
	protected ClientChannelInitializer(ClientConnection<?,?,Conf, CHandler> ownerConn, MsgCounter msgCounter) {
		super(ownerConn.eventHandler, msgCounter);
		this.ownerConn = ownerConn;
		this.handler = ownerConn.handler;
		this.config = ownerConn.config;
		this.routeStrategyRef = ownerConn.ownerSite.getRouteStrategyRef();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		logger.info("Failed to initialize a channel. Closing: {}. {}", ctx.channel(), cause);
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		super.initChannel(ch);
		ch.attr(ClientChannelAttribute.mainSiteRef).set(ownerConn.ownerSite.getMainSite());
		ch.attr(ClientChannelAttribute.ownerConnection).set(ownerConn);
		Endpoint endpoint = ch.attr(ChannelAttribute.connectionEndpoint).get();
		if(endpoint != null) endpoint.patchServiceName(config.host);
		ConnectionMode activeCM = routeStrategyRef.get().newReadyMode(ch.id().toString());
		ClientLimiter limiter = new ClientLimiter(activeCM, config);
		ch.attr(ClientChannelAttribute.activeCM).set(new AtomicReference<>(activeCM));
		ch.attr(ClientChannelAttribute.limiter).set(new AtomicReference<>(limiter));
		ch.attr(ChannelAttribute.counter).set(new AtomicReference<>(limiter.getCounter()));

	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		super.handlerAdded(ctx);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		super.handlerRemoved(ctx);
	}

	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
		logger.info("channelUnregistered {}", ctx.channel());
		super.channelUnregistered(ctx);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		logger.info("channelActive {}", ctx.channel());
		super.channelActive(ctx);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		logger.info("channelInactive {}", ctx.channel());
		super.channelInactive(ctx);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		logger.info("channelRead {}", ctx.channel());
		super.channelRead(ctx, msg);
	}
}
