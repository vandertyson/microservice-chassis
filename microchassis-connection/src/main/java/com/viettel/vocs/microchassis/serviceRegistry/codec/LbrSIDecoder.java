package com.viettel.vocs.microchassis.serviceRegistry.codec;

import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import com.viettel.vocs.microchassis.serviceRegistry.codec.context.LbrContext;
import com.viettel.vocs.microchassis.serviceRegistry.codec.handler.LbrHandler;
import com.viettel.vocs.microchassis.serviceRegistry.server.LbrServer;
import com.viettel.vocs.microchassis.tcp.codec.MsgHeader;
import com.viettel.vocs.microchassis.tcp.codec.TSIDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author tiennn18
 */
public class LbrSIDecoder extends TSIDecoder<LBRMsg, LbrHandler> {

	public LbrSIDecoder(LbrServer server, LbrHandler userHandler, ServerLimiter limiter) {
		super(server, userHandler, limiter);
	}

	@Override
	protected LBRMsg createDecodedMsg(ByteBuf body, String url, String messageId, MsgHeader headers) {
		return new LBRMsg(body, url, messageId, headers);
	}

	@Override
	public LbrContext wrapCtx(ChannelHandlerContext ctx, LBRMsg msg) {
		return new LbrContext(ctx, msg);
	}
}
