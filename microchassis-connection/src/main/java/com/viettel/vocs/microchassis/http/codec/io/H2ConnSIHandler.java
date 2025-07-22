/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.codec.io;

import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ServerCounter;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.metrics.InternalMetric;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.*;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author vttek
 * H2 server inbound handler
 */
public class H2ConnSIHandler extends Http2ConnectionHandler implements Http2FrameListener, HttpMsgDecoder {

	private static final Logger logger = LogManager.getLogger(H2ConnSIHandler.class);
	public IntObjectMap<HttpRequest> mapStream = new IntObjectHashMap<>();
	@Getter
	private final HttpServerHandler handler;
	@Getter
	private final HttpServerOptions options;
	@Getter
	private final ServerLimiter limiter; // null safe from ServerChannelInitializer
	@Getter
	private final ServerCounter counter; // null safe from ServerChannelInitializer
	private final EventHandler eventHandler;


	public H2ConnSIHandler(EventHandler eventHandler, HttpServerHandler h2Handler, HttpServerOptions options, Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings, ServerLimiter limiter, ServerCounter counter) {
		super(decoder, encoder, initialSettings);
		this.handler = h2Handler;
		this.options = options;
		this.eventHandler = eventHandler;
		this.counter = counter;
		this.limiter = limiter;
	}


	/**
	 * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a
	 * simple response via HTTP/2 on stream 1 (the stream specifically reserved
	 * for cleartext HTTP upgrade).
	 */
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (logger.isDebugEnabled()) logger.debug("userEventTriggered {}", evt);
//		if ( evt instanceof HttpServerUpgradeHandler.UpgradeEvent
//			|| evt instanceof Http2ConnectionPrefaceAndSettingsFrameWrittenEvent
//			|| evt instanceof CleartextHttp2ServerUpgradeHandler.PriorKnowledgeUpgradeEvent
//			|| (evt instanceof SslHandshakeCompletionEvent && ((SslHandshakeCompletionEvent) evt).isSuccess())
//		) {
//		}

		if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
			FullHttpRequest fullHttpRequest = ((HttpServerUpgradeHandler.UpgradeEvent) evt).upgradeRequest();
			int clientStartStreamId = 1;
			HttpRequest req = mapStream.computeIfAbsent(clientStartStreamId, k -> new HttpRequest(ctx.alloc(), fullHttpRequest.method(), fullHttpRequest.uri(), clientStartStreamId));
			req.addHeaders(fullHttpRequest.headers());
			ByteBuf content = fullHttpRequest.content();
//			if (content == null || content.readableBytes() == 0) {
//				chassisInvoke(new HttpServerContext(ctx, req));
//			} else {
			onDataRead(ctx, clientStartStreamId, content, 0, true);
//			}
		}
		super.userEventTriggered(ctx, evt);
		// Handle HTTP/2 connection ready event
		eventHandler.triggerEvent(new ContextEvent(ContextEvent.EventType.CHANNEL_ACTIVE, ctx));
		getUpgraded().set(true);
		if (logger.isDebugEnabled()) logger.debug("userEventTriggered cause channel Active by {}", evt);
	}

	@Getter
	private final AtomicBoolean upgraded = new AtomicBoolean(false);

	@Override
	public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
		int processed = data.readableBytes() + padding;
		HttpRequest req = endOfStream ? mapStream.remove(streamId) : mapStream.get(streamId);
		if (req == null) return processed; // discard bytes of processed msg
		req.addData(data);

		if (endOfStream) {
			NettyServer.serverMetric.incr(InternalMetric.Server.COUNT_HTTP1_REQUEST_RECEIVE);
			NettyServer.serverMetric.set(InternalMetric.Server.HISTO_HTTP1_REQUEST_PAYLOAD_SIZE, data.readableBytes());
			if (handler == null) {
				if (logger.isDebugEnabled()) logger.debug("No handler found");
				return processed;
			}
			httpDecode(ctx, req);
		}
		return processed;
	}


	@Override
	public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) {
		if (logger.isDebugEnabled()) logger.debug("Header2 read {}", headers);
		HttpRequest req = mapStream.computeIfAbsent(streamId, k -> new HttpRequest(ctx.alloc(), HttpMethod.valueOf(headers.method().toString()), headers.path().toString(), streamId));
		req.addHeaders(headers);
		if (endOfStream) httpDecode(ctx, req);
	}


	@Override
	public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) {
		onHeadersRead(ctx, streamId, headers, padding, endOfStream);
	}

	@Override
	public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) {
		if (logger.isDebugEnabled())
			logger.debug("onPriorityRead ctx={} streamId={} streamDependency={} weight={} exclusive={}",
				ctx.channel(), streamId, streamDependency, weight, exclusive);
	}

	@Override
	public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
		logger.warn("onRstStreamRead streamId {} errorCode {}", streamId, errorCode);
		/**
		 * RFC 7540 (HTTP/2) Guidelines
		 * According to the HTTP/2 specification:
		 *
		 * A stream reset by a client (via RST_STREAM) means the server must stop sending frames for that stream.
		 * The server must not reuse the stream ID for a new request and should follow the protocol's state transition rules.
		 */
		ctx.close();
//		eventHandler.triggerEvent(new ContextEvent(ContextEvent.EventType.CHANNEL_INACTIVE, ctx, message));
	}

	@Override
	public void onSettingsAckRead(ChannelHandlerContext ctx) {
		logger.info("onSettingsAckRead {}", ctx.channel());
	}

	@Override
	public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
		logger.info("onSettingsRead {}", settings);
	}

	@Override
	public void onPingRead(ChannelHandlerContext ctx, long data) {
		if (logger.isDebugEnabled()) {
			logger.debug("onPingRead ctx: {} data {}", ctx.channel(), data);
		}
		try (Http2ConnectionEncoder encoder = encoder()) {
			encoder.writePing(ctx, true, data, ctx.newPromise());
		}
		// 1. Client sends a PING frame: The client sends a PING frame to the server.
		// 2. Server receives the PING frame: The server reads the PING frame and sends back a PING ACK frame.
		// 3. Client receives the PING ACK frame: The client reads the PING ACK frame to confirm that the server is still responsive.
	}

	@Override
	public void onPingAckRead(ChannelHandlerContext ctx, long data) {
		if (logger.isDebugEnabled()) {
			logger.debug("onPingAckRead ctx {} data {}", ctx.channel(), data);
		}
		// TODO implement reverse ping from server flow
		//  1. Server sends a PING frame.
		//  2. Client receives the PING frame and responds with a PING ACK frame.
		//  -> 3. Server receives the PING ACK frame.


	}

	@Override
	public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) {
		if (logger.isDebugEnabled())
			logger.debug("onPushPromiseRead ctx={} streamId={} promisedStreamId={} headers={} padding={}",
				ctx.channel(), streamId, promisedStreamId, headers, padding);
	}

	@Override
	public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
		String message = "onGoAwayRead lastStreamId: " + lastStreamId + " errorCode " + errorCode + " debugData " + debugData + " len " + debugData.readableBytes();
		logger.warn(message);
		ctx.close();
	}

	@Override
	public ChannelFuture goAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData, ChannelPromise promise) {
		return super.goAway(ctx, lastStreamId, errorCode, debugData, promise);
	}

	@Override
	public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
		if (logger.isDebugEnabled()) {
			logger.debug("onWindowUpdateRead ctx={} streamId={} windowSizeIncrement={}",
				ctx.channel(), streamId, windowSizeIncrement);
		}
	}

	@Override
	public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
														 Http2Flags flags, ByteBuf payload) {
		if (logger.isDebugEnabled()) {
			logger.debug("onUnknownFrame ctx={} streamId={} frameType={} flags={} payload={}",
				ctx.channel(), streamId, frameType, flags, payload.toString(StandardCharsets.UTF_8));
		}
	}

	@Override
	public void onError(ChannelHandlerContext ctx, boolean outbound, Throwable cause) {
		super.onError(ctx, outbound, cause); //To change body of generated methods, choose Tools | Templates.
		logger.error(cause, cause);
	}

	@Override
	protected void onStreamError(ChannelHandlerContext ctx, boolean outbound, Throwable cause, Http2Exception.StreamException http2Ex) {
		super.onStreamError(ctx, outbound, cause, http2Ex); //To change body of generated methods, choose Tools | Templates.
		logger.error(cause);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		super.exceptionCaught(ctx, cause);
		ctx.close();
	}
}
