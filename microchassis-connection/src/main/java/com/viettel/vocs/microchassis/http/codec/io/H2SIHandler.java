/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.codec.io;

import com.viettel.vocs.microchassis.codec.context.http.HttpMockServerContext;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.codec.HttpMsg;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.metrics.InternalMetric;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;

/**
 * @author vttek
 * h2 and h1 upgraded handler, must except all incoming type of H2
 */
public class H2SIHandler extends H1SIHandler {

	private final Http2ConnectionEncoder encoder;

	/**
	 * @param h1siHandler  // H2 upgraded from H1 // server receive incoming by HTTP1 then cast to 2 later
//	 * @param h2SIHandler
	 */
	public H2SIHandler(H1SIHandler h1siHandler, Http2ConnectionEncoder encoder, MsgCounter msgCounter) {
		super(h1siHandler.eventHandler, h1siHandler.handler, h1siHandler.counter, h1siHandler.limiter, msgCounter);
		this.encoder = encoder;
	}
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx); //To change body of generated methods, choose Tools | Templates.
		if (logger.isDebugEnabled()) logger.debug("[Server channel inactive]{channel={}}", ctx.channel());
		NettyServer.serverMetric.incr(InternalMetric.Server.COUNT_HTTP_CONNECTION_DOWN);
	}
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fMsg) throws Exception {
		if(logger.isDebugEnabled()) logger.debug("Server receive Http2 msg of type {}", fMsg.getClass().getSimpleName());
		if (fMsg instanceof HttpMockServerContext) {
			// HTTP2 response is handled after refactor in Http2Context.send(futures...) and nextSendPipeline()
		} else {
			// If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
			if (HttpMsg.isHttp2(fMsg)) {
				HttpServerContext http2ServerContext = new HttpServerContext(ctx, new HttpRequest(fMsg));
				// flow handle as a full endOfStream HTTP2 request bcause has streamId
				// but message is created as Http1, so bad request here -> response 400
				if (logger.isDebugEnabled()) logger.debug("[DefaultFullHttpRequest with streamID]{ctx={}, streamId={}, req={}}", ctx.channel(), http2ServerContext.streamId, http2ServerContext.getInMsg());
				http2ServerContext.send(HttpResponseStatus.BAD_REQUEST);
			} else { // flow handle as HTTP1 bcause no streamId
				if (logger.isDebugEnabled()) logger.debug("[No stream id found. Process http1]{ctx={}, req={}}", ctx.channel(), fMsg);
				super.channelRead0(ctx, fMsg);
			}
		}
	}


	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (logger.isDebugEnabled()) logger.debug("Event {} {}", evt.getClass().getSimpleName(), evt);

		super.userEventTriggered(ctx, evt);
	}
}
