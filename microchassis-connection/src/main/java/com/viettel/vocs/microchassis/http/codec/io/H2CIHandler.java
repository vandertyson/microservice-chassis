/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.codec.io;

import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process {@link io.netty.handler.codec.http.FullHttpResponse} translated from
 * HTTP/2 frames
 */
public class H2CIHandler extends HCIHandler {

	private static final Logger logger = LogManager.getLogger(H2CIHandler.class);
	public H2CIHandler(EventHandler eventHandler, HttpClientHandler handler2, MsgCounter msgCounter) {
		super(handler2, eventHandler, msgCounter);
		// Use a concurrent map because we add and iterate from the main thread (just for the purposes of the example),
		// but Netty also does a get on the map when messages are received in a EventLoop thread.
//        h2Handler = (HttpClientHandler) handlers.get(ChassisConst.SupportVersion.HTTP2_0);
	}


	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) {
		if (logger.isDebugEnabled()) {
			logger.debug("Server channelUnregistered " + ctx);
		}
	}

	private final AtomicInteger maxReceiveStreamID = new AtomicInteger();

	public int getMaxReceiveStreamID() {
		return maxReceiveStreamID.get();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpResponse res) {
		try {
			HttpResponse inMsg = stack(new HttpResponse(HttpResponse.toFullRetain(res))); // to read in
			if (!inMsg.isHttp2()) {
				if (logger.isDebugEnabled())
					logger.error("Message received for unknown stream id " + inMsg.getStreamId());
				throw new IllegalArgumentException("StreamId of Http2 client incoming message is null, this could cause by upgrade from Http1 failed");
//			} else if (
//				inMsg.getStreamId() == 1 // wrong server streamId response, must from 2
//					|| inMsg.getStreamId() <= lastReceiveStreamID.get() // receive old streamId ?
//			) {
//				if (logger.isDebugEnabled())
//					logger.warn("[stream:{}] Wrong stream, content: {}", inMsg.getStreamId(), inMsg.toStringUTF8());
			} else { // new response
				if (inMsg.getStreamId() > maxReceiveStreamID.get()) maxReceiveStreamID.set(inMsg.getStreamId());
				if (logger.isDebugEnabled()) logger.debug("Received new stream id " + maxReceiveStreamID);
				super.channelRead0(ctx, inMsg);
			}
		} catch (Exception e) {
			logger.error(e, e);
			ctx.writeAndFlush(new DefaultFullHttpResponse(res.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR,
				ctx.alloc().buffer().writeBytes(("H2CI encounter error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8))));
		}
	}

}
