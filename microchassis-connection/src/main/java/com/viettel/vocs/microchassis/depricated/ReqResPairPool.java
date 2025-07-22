package com.viettel.vocs.microchassis.depricated;

import com.viettel.vocs.microchassis.codec.context.ChassisReplyContext;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import org.jctools.queues.MpmcArrayQueue;

import java.util.concurrent.atomic.AtomicLong;

public class ReqResPairPool<Req extends ChassisMgrableMsg, Res extends ChassisMgrableMsg, ReuseServerCtx extends ChassisReplyContext<Req, Res>> {
	private final MpmcArrayQueue<Res> resPool; // chi can luu resMsg vi reqMsg tao ra boi NettyIncomming decoder, Res do user tao ra
	private final MpmcArrayQueue<ReuseServerCtx> ctxPool;
	protected final AtomicLong createdMsg = new AtomicLong();

	public AtomicLong getCreatedMsg() {
		return createdMsg;
	}

	private final AtomicLong createdContext = new AtomicLong();

	public AtomicLong getCreatedContext() {
		return createdContext;
	}

	public ReqResPairPool(int initMsgPoolSize, int initCtxPoolSize) {
		this.resPool = new MpmcArrayQueue<>(initMsgPoolSize);
		this.ctxPool = new MpmcArrayQueue<>(initCtxPoolSize);
	}

	public float queueContextUtilization() {
		return (float) ctxPool.size() / createdContext.get();
	}

	public String queueContextStat() {
		return String.format("%d/%d", ctxPool.size(), createdContext.get());
	}

	public float queueMsgUtilization() {
		return (float) resPool.size() / createdMsg.get();
	}

	public String queueMsgStat() {
		return String.format("%d/%d", resPool.size(), createdMsg.get());
	}

	public ReuseServerCtx addCtx(ReuseServerCtx newServerCtx) {
		createdContext.incrementAndGet();
		return newServerCtx;
	}

	public boolean returnCtx(ReuseServerCtx ctx) {
		// TODO ? neu ctx tao ra do incoming msg qua nhieu thi sao ?
		return ctxPool.offer(ctx);
	}
	public ReuseServerCtx getCtx() {
		return ctxPool.poll();
	}
}
