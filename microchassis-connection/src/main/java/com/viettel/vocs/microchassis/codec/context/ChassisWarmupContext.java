package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.base.Endpoint;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public abstract class ChassisWarmupContext<InChargeMsg extends ChassisMgrableMsg, CorrespondingMsg extends ChassisMgrableMsg>
	extends ChassisNoReplyNoContext<InChargeMsg> // it not contain ctx
	implements ReplyContext<InChargeMsg, CorrespondingMsg> // but it act as ReplyCtx with fake send and context, channel actions
{
	@Override
	public ChannelHandlerContext getCtx() {
		return null;
	}

	@Override
	public String getCtxIp() {
		return "localhost";
	}

	@Override
	public Endpoint getEndpoint() {
		return Endpoint.newEndpoint("localhost", 0);
	}

	@Override
	public boolean validateCtx() {
		return false;
	}

	@Override
	public Channel getChannel() {
		return null;
	}

	@Override
	public ChannelPromise newChannelPromiseThenResolve(GenericFutureListener<ChannelFuture>... futures) {
		return null;
	}

	@Override
	public ChannelPromise newChannelPromise(GenericFutureListener<ChannelFuture>... futures) {
		return null;
	}


	private final CompletableFuture<CorrespondingMsg> future = new CompletableFuture<>();
	protected final CorrespondingMsg coMsg; // only set in here and at setResponse of TcpBasedContext and HttpReplyContext
	protected abstract CorrespondingMsg newCoMsg(int outInitSize, String path, String msgId); // only allow to call in ReplyContext Class

	protected abstract CorrespondingMsg newCoMsg(String path, String msgId); // only allow to call in ReplyContext Class
	protected final ByteBufAllocator outBufAllocator;
	@Override
	public CorrespondingMsg getCoMsg() {
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
	public CompletableFuture<CorrespondingMsg> getFuture() {
		return future;
	}

	public ChassisWarmupContext(ByteBufAllocator allocator, InChargeMsg req) {
		super(req);
		this.outBufAllocator = Objects.requireNonNull(allocator, "Warmup required Allocator not null");
		coMsg = newCoMsg(getInPath(), getInID());
	}

	public ChassisWarmupContext(ByteBufAllocator allocator, InChargeMsg req, int bufInitSize) {
		super(req);
		this.outBufAllocator = Objects.requireNonNull(allocator, "Warmup required Allocator not null");
		coMsg = newCoMsg(bufInitSize, getInPath(), getInID());
	}

	public void complete() {
		future.complete(getCoMsg());
	}

	@Override
	public final void send(GenericFutureListener<ChannelFuture>... futures) {
		// ignore if warmup, // serverContext then done flow, clientContext thi khong warmup
		complete();
	}
}
