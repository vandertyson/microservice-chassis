package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import io.netty.channel.ChannelHandlerContext;



public class ChassisNoReplyContext<InChargeMsg extends ChassisMgrableMsg>
	extends ChassisNoReplyNoContext<InChargeMsg>
	implements NoReplyContext<InChargeMsg> {
	// CTX area
	protected ChannelHandlerContext ctx;

	@Override
	public String toString() {
		return String.format("%s{channel=%s, request=%s, reqType=%s}", getClass().getSimpleName(), getChannelString(), getInMsg(), getInMsg().msgType);
	}


	@Override
	public ChannelHandlerContext getCtx() {
		return ctx;
	}

	public ChassisNoReplyContext(ChannelHandlerContext ctx, InChargeMsg inChargedMsg) {
		super(inChargedMsg);
		this.ctx = ctx; // allow null, all API must check validateCtx() before ctx.call
	}
}

