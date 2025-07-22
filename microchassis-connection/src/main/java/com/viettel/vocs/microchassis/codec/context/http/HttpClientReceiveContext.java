package com.viettel.vocs.microchassis.codec.context.http;

import com.viettel.vocs.microchassis.codec.context.ChassisNoReplyContext;
import com.viettel.vocs.microchassis.codec.context.ClientReceiveContext;
import com.viettel.vocs.microchassis.codec.context.CtxHolder;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import lombok.Getter;

/**
 * @author tiennn18
 */
public class HttpClientReceiveContext extends ChassisNoReplyContext<HttpResponse> implements ClientReceiveContext<HttpResponse>, HttpContext {  // Response is a must for ReceiveCtx -> Res = Incharge, however Req can be found or can not identify at async handler -> Req = Co, Co maybe null
//	public static HttpClientReceiveContext wrapCtx(ChannelHandlerContext ctx, FullHttpResponse msg, long sentTime) {
//		HttpClientReceiveContext hctx = wrapCtx(ctx, msg); // incoming is response, so this is client handler, Http1Context<Res, Req>
//		hctx.getInMsg().setSentTime(sentTime);
//		return hctx;
//	}
	public static HttpClientReceiveContext wrapCtx(ChannelHandlerContext ctx, FullHttpResponse in) {
		return new HttpClientReceiveContext(ctx, in);
	}


	@Override
	public boolean validateCtx() {
		return super.validateCtx();
	}

	@Override
	public Channel getChannel() {
		return super.getChannel();
	}

	@Getter private final boolean isHttp2 = CtxHolder.isHttp2(this.ctx);
	public final int streamId = inChargedMsg.getStreamId();
	public HttpClientReceiveContext(ChannelHandlerContext ctx, FullHttpResponse in) {
		this(ctx, new HttpResponse(in));
	}
	public HttpClientReceiveContext(ChannelHandlerContext ctx, HttpResponse in) {
		super(ctx, in);
	}

	@Override
	public final String getInPath() {
		return inChargedMsg.getUrl(); // in here return empty string "", because response do not have path, it is corresponding
	}
}
