package com.viettel.vocs.microchassis.http.server;

import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.server.ServerChannelInitializer;
import com.viettel.vocs.microchassis.http.client.HttpChannelAttribute;
import com.viettel.vocs.microchassis.http.client.HttpChannelInitializer;
import com.viettel.vocs.microchassis.http.codec.io.H1SIHandler;
import com.viettel.vocs.microchassis.http.codec.io.H2ConnSIHBuilder;
import com.viettel.vocs.microchassis.http.codec.io.H2ConnSIHandler;
import com.viettel.vocs.microchassis.http.codec.io.H2SIHandler;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class HttpServerInitializer extends ServerChannelInitializer<HttpServerOptions, HttpServerHandler> implements HttpChannelInitializer {
	class Http2SSettingsHandler extends SimpleChannelInboundHandler<HttpMessage> {
		private final AtomicBoolean upgradedState;
		private final ChannelHandler afterThisHandler;
		private final ChannelHandler[] replaceHandlers;

		public Http2SSettingsHandler(ChannelHandler afterThisHandler, AtomicBoolean upgradedState, ChannelHandler... removeHandlers){
			this.upgradedState = upgradedState;
			this.afterThisHandler = afterThisHandler;
			this.replaceHandlers = removeHandlers;
		}
		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			super.channelActive(ctx);
			if (eventHandler != null)
				eventHandler.triggerEvent(new ContextEvent(ContextEvent.EventType.CHANNEL_ACTIVE, ctx));
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			super.channelInactive(ctx);
			if (eventHandler != null)
				eventHandler.triggerEvent(new ContextEvent(ContextEvent.EventType.CHANNEL_INACTIVE, ctx));
		}

		/**
		 * upgrade failed handler
		 */
		@Override
		protected void channelRead0(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpMessage req) {
			if(!upgradedState.get()) {
				if (logger.isDebugEnabled())
					logger.debug(String.format("Directly talking: %s (no upgrade was attempted)", req.protocolVersion()));
				ChannelPipeline pipeline = ctx.pipeline()
					.addAfter(ctx.name(), null, afterThisHandler)
					.replace(this, null, new HttpObjectAggregator(config.http1.maxContentLength));
				if(replaceHandlers != null && replaceHandlers.length > 0) Arrays.stream(replaceHandlers).forEach(handler1 -> {
					try {
						pipeline.remove(handler1);
					} catch (NoSuchElementException | NullPointerException ignored){}
				});
				ctx.fireChannelRead(ReferenceCountUtil.retain(req));
			}
		}
	}
	private static final Logger logger = LogManager.getLogger(HttpServerInitializer.class);
	protected static final Http2FrameLogger serverFrameLogger = new Http2FrameLogger(frameLoggerLevel, HttpServerInitializer.class);
	protected final SslContext sslCtx;

	public HttpServerInitializer(HttpServer server,
															 SslContext sslCtx,
															 HttpServerHandler handlers) {
		super(server, handlers);
		this.sslCtx = sslCtx;
	}

	@Override
	public void initChannel(SocketChannel ch) throws Exception { /**TODO Them cau hinh disable validate header H1**/
		super.initChannel(ch);
		ChannelPipeline pipeline = ch.pipeline();
		HttpServerCodec sourceCodec = new HttpServerCodec();
		H1SIHandler h1SIHandler = new H1SIHandler(eventHandler, handler, server.getCounter(), limiter, msgCounter);
		H2ConnSIHandler h2CSIHandler = new H2ConnSIHBuilder(eventHandler, handler, config, server.getCounter(), limiter).build();
		H2SIHandler h2SIHandler = new H2SIHandler(h1SIHandler, h2CSIHandler.encoder(), msgCounter);
		Http2SSettingsHandler http2SSettingsHandler = new Http2SSettingsHandler(h1SIHandler, h2CSIHandler.getUpgraded(), h2SIHandler);
		ch.attr(HttpChannelAttribute.http2encoder).set(new AtomicReference<>(h2CSIHandler.encoder()));

		if (sslCtx != null) {  /** Configure the pipeline for TLS NPN negotiation to HTTP/2. */
			if (logger.isDebugEnabled()) logger.info("Establish Ssl channel");
			pipeline
				.addLast(sslCtx.newHandler(ch.alloc()))
				.addLast(new HttpServerExpectContinueHandler())
				.addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
					@Override
					protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
						switch (protocol) {
							case ApplicationProtocolNames.HTTP_2:
								ctx.pipeline()
									.addLast(h2CSIHandler)
									.addLast(http2SSettingsHandler) // for fail upgrade
									.addLast(h2SIHandler);
								break;
							case ApplicationProtocolNames.HTTP_1_1:
								ctx.pipeline()
									.addLast(new HttpServerCodec())
									.addLast(new HttpObjectAggregator(config.http1.maxContentLength))
									.addLast(h1SIHandler);
								break;
							default:
								ctx.close();
								throw new IllegalStateException("unknown protocol: " + protocol);
						}
					}
				});
		} else {  /** Configure the pipeline for a cleartext upgrade from HTTP to HTTP/2.0 */
			if (logger.isDebugEnabled()) logger.info("Establish plain text channel");
			pipeline.addLast(
					new CleartextHttp2ServerUpgradeHandler(sourceCodec,
						new HttpServerUpgradeHandler(sourceCodec, protocol -> AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)
							? new Http2ServerUpgradeCodec(h2CSIHandler) : null, config.maxHttpContentLength()), h2CSIHandler))
				.addLast(http2SSettingsHandler);
		}
		// below are application handlers
		pipeline
			.addLast(new ChannelInboundHandlerAdapter() {
			/**
			 * Class that logs any User Events triggered on this channel.
			 */
			@Override
			public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
				if (logger.isDebugEnabled()) {
					logger.debug("User Event Triggered: {}", evt);
				}
				ctx.fireUserEventTriggered(evt);
			}
		});
		if (logger.isDebugEnabled()) logger.debug("Packed HTTP server pipelines {}", pipeline);
	}

}
