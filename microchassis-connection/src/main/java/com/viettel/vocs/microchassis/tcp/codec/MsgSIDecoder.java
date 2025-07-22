package com.viettel.vocs.microchassis.tcp.codec;

import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import com.viettel.vocs.microchassis.tcp.server.TcpServer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import static com.viettel.vocs.microchassis.tcp.codec.Msg.logger;

public class MsgSIDecoder extends TSIDecoder<Msg, TcpHandler> {
	public MsgSIDecoder(TcpServer server, TcpHandler handlers, ServerLimiter limiter) {
		super(server, handlers, limiter);
	}

	@Override
	public TcpContext wrapCtx(ChannelHandlerContext ctx, Msg msg) {
		if (logger.isInfoEnabled()) msg.headers()
			.onHeaderNotNull("lastFlush", (Handler<String>) lastFlushStr ->
				msg.headers.set("totalFlush", String.valueOf(msg.getDecodeTime() - Long.parseLong(lastFlushStr)))

			);
		return new TcpContext(ctx, msg);
	}

	@Override
	protected Msg createDecodedMsg(ByteBuf body, String url, String messageId, MsgHeader headers) {
		return new Msg(body, url, messageId, headers);
	}
}
