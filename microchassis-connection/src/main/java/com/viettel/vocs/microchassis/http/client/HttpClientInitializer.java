package com.viettel.vocs.microchassis.http.client;

import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.codec.context.ReplyContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.connection.client.ChannelAttribute;
import com.viettel.vocs.microchassis.connection.client.ClientChannelInitializer;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.io.H1CIHandler;
import com.viettel.vocs.microchassis.http.codec.io.H2CIHandler;
import com.viettel.vocs.microchassis.http.codec.io.InboundHttp2ToHttpAdapterBuilder;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.viettel.vocs.microchassis.http.codec.Http2CodecUtils.CUSTOM_SENSITIVE_HTTP2_HEADERS;

/**
 * @author vttek
 */
public class HttpClientInitializer extends ClientChannelInitializer<HttpClientOptions, HttpClientHandler> implements HttpChannelInitializer {
	private static final Logger logger = LogManager.getLogger(HttpClientInitializer.class);
	protected static final Http2FrameLogger clientFrameLogger = new Http2FrameLogger(frameLoggerLevel, HttpClientInitializer.class);

	protected final SslContext sslCtx;


	public HttpClientInitializer(HttpClientConnection conn, MsgCounter msgCounter) {
		super(conn, msgCounter);
		this.sslCtx = conn.sslCtx;
	}

	private Http2ConnectionHandler newH2CCIHandler(SocketChannel ch) throws Http2Exception {
		final Http2Connection connection = new DefaultHttp2Connection(false);

		// decor flow control
		DefaultHttp2LocalFlowController defaultHttp2LocalFlowController = new DefaultHttp2LocalFlowController(connection, 0.5f, true);
		defaultHttp2LocalFlowController.initialWindowSize(config.http2.initialWindowSize);
		connection.local().flowController(defaultHttp2LocalFlowController);
		Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection,
			new Http2OutboundFrameLogger(
				new DefaultHttp2FrameWriter(CUSTOM_SENSITIVE_HTTP2_HEADERS, true),
				clientFrameLogger));
		if (Http2CodecUtil.DEFAULT_MAX_QUEUED_CONTROL_FRAMES != 0)
			encoder = new com.viettel.vocs.microchassis.http.codec.io.Http2ControlFrameLimitEncoder(encoder, Http2CodecUtil.DEFAULT_MAX_QUEUED_CONTROL_FRAMES);
		if (config.http2.encoderEnforceMaxConcurrentStreams)
			encoder = new StreamBufferingEncoder(encoder);
		DefaultHttp2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder,
			new Http2InboundFrameLogger(
				new DefaultHttp2FrameReader(
					new DefaultHttp2HeadersDecoder(
						true, config.http2.maxHeaderListSize(),/* initialHuffmanDecodeCapacity= */ -1)),
				clientFrameLogger),
			Http2PromisedRequestVerifier.ALWAYS_VERIFY, true, true);
		com.viettel.vocs.microchassis.http.codec.io.InboundHttp2ToHttpAdapter adapter = InboundHttp2ToHttpAdapterBuilder.build(connection, config.http2.maxContentLength);

		Http2ConnectionHandler h2CCIHandler = new HttpToHttp2ConnectionHandlerBuilder()
			.frameListener(adapter).codec(decoder, encoder).build();

		ch.attr(HttpChannelAttribute.http2encoder).set(new AtomicReference<>(encoder));
		ch.attr(HttpClientChannelAttribute.h2toh1Adapter).set(adapter);
		return h2CCIHandler;
	}
	/**
	 * Wait for this handler to be added after the upgrade to HTTP/2, and for initial preface
	 * handshake to complete.
	 *
	 * @param timeout Time to wait
	 * @param unit    {@link java.util.concurrent.TimeUnit} for {@code timeout}
	 * @throws Exception if timeout or other failure occurs
	 */
	public static void awaitSettings(Channel ch, long timeout, TimeUnit unit) throws Exception {
		ChannelPromise settingPromise = ch.attr(HttpClientChannelAttribute.settingPromise).get();
		if (!settingPromise.awaitUninterruptibly(timeout, unit))
			throw new IllegalStateException("Timed out waiting for settings");
		if (!settingPromise.isSuccess())
			throw new RuntimeException(settingPromise.cause());
	}
	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		super.initChannel(ch);
		ch.attr(HttpClientChannelAttribute.promiseMap).set(new ConcurrentHashMap<>());
		H2CIHandler h2CIHandler = new H2CIHandler(eventHandler, handler, msgCounter);
		Http2ConnectionHandler h2CCIHandler = newH2CCIHandler(ch);
		HttpClientCodec sourceCodec = new HttpClientCodec(); // included outbound and inbound p2p codec, so no need decoder and encoder
		H1CIHandler h1CIHandler = new H1CIHandler(eventHandler, handler, msgCounter);
		ChannelPromise settingPromise = ch.newPromise();
		ch.attr(HttpClientChannelAttribute.syncFuture).set(new AtomicReference<>(null));
		ch.attr(HttpClientChannelAttribute.settingPromise).set(settingPromise);
		SimpleChannelInboundHandler<Http2Settings> settingsHandler = new SimpleChannelInboundHandler<>() {
			/**
			 * Reads the first {@link Http2Settings} obje
			 * ct and notifies a {@link io.netty.channel.ChannelPromise}
			 */
			@Override
			protected void channelRead0(ChannelHandlerContext ctx, Http2Settings msg) {
				logger.info("Receive HTTP/2 setting. ctx={}, setting={}", ctx.channel(), msg);
				settingPromise.setSuccess();
				// Only care about the first settings message
				ctx.pipeline().remove(this);
			}
		};

		ChannelPipeline pipeline = ch.pipeline();
		if (sslCtx != null) {
			pipeline.addLast(sslCtx.newHandler(ch.alloc(), config.host, config.port));
			if (logger.isDebugEnabled()) logger.info("Establish Ssl channel");
		} else if (logger.isDebugEnabled()) logger.info("Establish plain text channel");

		if (ChassisConst.SupportVersion.HTTP1_1.equals(config.initProto)
			|| ChassisConst.SupportVersion.HTTP1_0.equals(config.initProto)) {
			if (config.negotiable) { // http1 upgradable
				logger.info("Creating HTTP1upgradable connection. clientId={}, endpoint={}", config.id, ChannelAttribute.getEndpoint(ch));
				pipeline.addLast(sourceCodec, new HttpClientUpgradeHandler(sourceCodec, new Http2ClientUpgradeCodec(h2CCIHandler), config.maxHttpContentLength()), new ChannelInboundHandlerAdapter() {
					@Override
					public void channelActive(ChannelHandlerContext ctx) {
						/**
						 * A handler that triggers the cleartext upgrade to HTTP/2 by sending an
						 * initial HTTP request.
						 */
						HttpRequest upgradeRequest = new HttpRequest(ctx.alloc(), HttpMethod.GET, "/");
						upgradeRequest.setMgr(MsgType.UPGRADE); // TODO do not count for management requests
						upgradeRequest.headers().set(HttpHeaderNames.HOST, Endpoint.remote(ctx).patchServiceName(config.host).toFormalString());
						// Set HOST header as the remote peer may require it.
						ReplyContext.sendByChannel(ctx.channel(), upgradeRequest, upgradeRequest);
						ctx.fireChannelActive();
						pipeline
							.remove(this) // Done with this handler, remove it from the pipeline.
							.addLast(settingsHandler, h2CIHandler);
					}
				});
			} else {// http1 only
				logger.info("Creating HTTP1only connection. clientId={}, endpoint={}", config.id, ChannelAttribute.getEndpoint(ch));
				pipeline.addLast(sourceCodec, new HttpObjectAggregator(config.http1.maxContentLength), h1CIHandler);
			}
		} else if (ChassisConst.SupportVersion.HTTP2_0.equals(config.initProto)) {
			if (config.negotiable) { // http2 fallback-able
				logger.info("Creating HTTP2fallbackable connection. clientId={}, endpoint={}", config.id, ChannelAttribute.getEndpoint(ch));
				pipeline.addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) { // We must wait for the handshake to finish and the protocol to be negotiated before configuring// the HTTP/2 components of the pipeline.
					@Override
					protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
						switch (protocol) {
							case ApplicationProtocolNames.HTTP_2:
								ctx.pipeline()
									.addLast(h2CCIHandler)
									.addLast(settingsHandler)
									.addLast(h2CIHandler);
								break;
//							case ApplicationProtocolNames.HTTP_1_1: // fallback
//								configureHttp1(ctx.channel());
//								break;
							default:
								ctx.close();
								throw new IllegalStateException("unknown protocol: " + protocol);
						}
					}
				});
			} else { // http2 only
				logger.info("Creating HTTP2only connection. clientId={}, endpoint={}", config.id, ChannelAttribute.getEndpoint(ch));
				pipeline.addLast(h2CCIHandler, settingsHandler, h2CIHandler);
			}
		}
		// below are application handlers
		if (logger.isDebugEnabled()) logger.debug("Packed HTTP client pipelines {}", pipeline);
	}
}
