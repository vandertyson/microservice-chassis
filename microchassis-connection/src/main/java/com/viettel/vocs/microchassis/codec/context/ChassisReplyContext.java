/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.common.hashing.CheckSumEngine;
import com.viettel.vocs.microchassis.codec.MsgType;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;

import java.util.Objects;


/**
 * context that distinct Req and Res, like HttpReq, HttpRes
 * => distinct inbound/outbound server handler
 *
 * @param <InMsg>
 * @param <CoMsg>
 */
public abstract class ChassisReplyContext<InMsg extends ChassisMgrableMsg, CoMsg extends ChassisMgrableMsg>
	extends ChassisNoReplyContext<InMsg>
	implements ReplyContext<InMsg, CoMsg> {
	// Response area
	protected final CoMsg coMsg; // only set in here and at setResponse of TcpBasedContext and HttpReplyContext
	@Override
	public CoMsg getCoMsg() {
		return coMsg;
	}

	@Override
	public MsgType getCoMsgType() {
		return coMsg.msgType;
	}

	@Override
	public boolean isCoMgr() {
		return coMsg.isMgr();
	}

	protected final ByteBufAllocator outBufAllocator;
	protected CheckSumEngine checkSumEngine;
	@Override
	public void setCheckSumEngine(CheckSumEngine engine) {
		checkSumEngine = engine;
		super.setCheckSumEngine(engine); // set to inCharge
		coMsg.setCheckSumEngine(engine);
	}

	@Override
	public CheckSumEngine getCheckSumEngine() {
		if(checkSumEngine == null) { // bi direct set before return
			CheckSumEngine inCheckSumEnging;
			if((inCheckSumEnging = super.getCheckSumEngine())!= null){
				setCheckSumEngine(inCheckSumEnging);
			}
		}
		return checkSumEngine;
	}

	protected abstract CoMsg newCoMsg(int outInitSize, String path, String msgId); // only allow to call in ReplyContext Class

	protected abstract CoMsg newCoMsg(String path, String msgId); // only allow to call in ReplyContext Class


	@Override
	public void operationComplete(ChannelFuture future) throws Exception {
		super.operationComplete(future); // call to complete incharge
		coMsg.operationComplete(future);
	}

	protected ChassisReplyContext(ChannelHandlerContext ctx, InMsg req) {
		super(Objects.requireNonNull(ctx), req);
		this.outBufAllocator = Objects.requireNonNull(ctx, "Context required ChannelHandlerContext not null").alloc();
		coMsg = newCoMsg(getInPath(), getInID());
	}

	protected ChassisReplyContext(ChannelHandlerContext ctx, InMsg req, int bufInitSize) {
		super(Objects.requireNonNull(ctx), req);
		this.outBufAllocator = Objects.requireNonNull(ctx, "Context required ChannelHandlerContext not null").alloc();
		coMsg = newCoMsg(bufInitSize, getInPath(), getInID());
	}
}
