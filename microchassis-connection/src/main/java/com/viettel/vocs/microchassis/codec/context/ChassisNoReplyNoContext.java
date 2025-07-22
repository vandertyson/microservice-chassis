package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.common.hashing.CheckSumEngine;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.common.hashing.Checksumable;
import io.netty.channel.ChannelFuture;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ChassisNoReplyNoContext<InChargeMsg extends ChassisMgrableMsg>
	implements Checksumable, NoReplyNoContext<InChargeMsg> {
	protected InChargeMsg inChargedMsg;
	public final Map<String, List<String>> getInParams(){
		return inChargedMsg.getInParams();
	}
	@Override
	public String getInPath() {
		return inChargedMsg.getUrl();
	}

	@Override
	public String getInID() {
		return inChargedMsg.getMessageId();
	}

	@Override
	public void decompose() {
		inChargedMsg.decompose();
	}

	@Override
	public void operationComplete(ChannelFuture future) throws Exception {
		inChargedMsg.operationComplete(future);
	}

	// Request area
	@Override
	public InChargeMsg getInMsg() { // request from client, it is Res decoded msg
		return inChargedMsg;
	}


	@Override
	public byte[] getInData() {
		return inChargedMsg.getContent();
	}

	public ChassisNoReplyNoContext(InChargeMsg inChargedMsg) {
		this.inChargedMsg = Objects.requireNonNull(inChargedMsg, "Cannot create context without in charged message");
	}

	@Override
	public MsgType getInMsgType() {
		return inChargedMsg.msgType;
	}

	public boolean isInMgr() {
		return getInMsgType() != null;
	}

	@Override
	public void setCheckSumEngine(CheckSumEngine engine) {
		inChargedMsg.setCheckSumEngine(engine);
	}

	@Override
	public CheckSumEngine getCheckSumEngine() {
		return inChargedMsg.getCheckSumEngine();
	}
	@Override
	public String toString() {
		return String.format("%s{request=%s, reqType=%s}", this.getClass().getSimpleName(), getInMsg(), getInMsg().msgType);
	}


}
