package com.viettel.vocs.microchassis.codec.context.tcp;

import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author tiennn18
 */
public class TcpContext extends TcpBasedContext<Msg> {
	public TcpContext(ChannelHandlerContext ctx, Msg inMsg) {
		super(ctx, inMsg);
	}

	public TcpContext(ChannelHandlerContext ctx, Msg inMsg, int outBufInitSize) {
		super(ctx, inMsg, outBufInitSize);
	}

	@Override
	public Msg newCoMsg(int outInitSize, String path, String msgId) {
		return new Msg(outBufAllocator, outInitSize, path, msgId);
	}

	@Override
	public Msg newCoMsg(String path, String msgId) {
		return new Msg(outBufAllocator, path, msgId);
	}
}
