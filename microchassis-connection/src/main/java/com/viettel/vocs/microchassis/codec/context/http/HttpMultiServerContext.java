package com.viettel.vocs.microchassis.codec.context.http;

import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpMultiResponse;
import io.netty.channel.ChannelHandlerContext;


/**
 * multi reply ctx
 */
public class HttpMultiServerContext extends HttpServerContext {

	@Override
	public HttpMultiResponse newCoMsg(int outInitSize, String path, String msgId) {
		return new HttpMultiResponse(ctx.alloc(), getInID());
	}

	protected HttpMultiServerContext(ChannelHandlerContext ctx, HttpRequest inReqReq) {
		super(ctx, inReqReq);
	}
}
