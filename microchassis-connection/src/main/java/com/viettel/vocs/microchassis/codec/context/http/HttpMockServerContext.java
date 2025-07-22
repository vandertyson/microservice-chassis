package com.viettel.vocs.microchassis.codec.context.http;

import io.netty.channel.ChannelHandlerContext;

/**
 * fake req to create server send response to client
 */
public class HttpMockServerContext extends HttpServerContext {
	public HttpMockServerContext(ChannelHandlerContext ctx) {
		super(ctx, null);
	}
}
