package com.viettel.vocs.microchassis.serviceRegistry.codec.context;

import com.viettel.vocs.microchassis.codec.context.tcp.TcpBasedContext;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRMsg;
import io.netty.channel.ChannelHandlerContext;

public class LbrContext extends TcpBasedContext<LBRMsg> {

	public LbrContext(ChannelHandlerContext ctx, LBRMsg request) {
		super(ctx, request);
	}

	@Override
	public LBRMsg newCoMsg(int outInitSize, String path, String msgId) {
		return newCoMsg(path, msgId);
	}


	@Override
	public LBRMsg newCoMsg(String path, String msgId) {
		return new LBRMsg(outBufAllocator, path, msgId, getInMsg().headers());
	}

	public void ack(String state) {
		coMsg.headers().set(LBRMsg.STATE, state);
		getCoMsg().validateLbr();
		send();
	}
}
