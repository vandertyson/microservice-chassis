package com.viettel.vocs.microchassis.codec.handler.tcp;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.context.ChassisReplyContext;
import com.viettel.vocs.microchassis.codec.context.NoReplyContext;
import com.viettel.vocs.microchassis.codec.handler.DropableHandler;
import com.viettel.vocs.microchassis.codec.handler.Handler;

interface ChassisServerHandler<InChargeMsg extends ChassisMgrableMsg, CtxResponse extends NoReplyContext<InChargeMsg>> {
	void chassisHandle(CtxResponse ctx);

	void chassisDropHandle(InChargeMsg failedReq, boolean isSent);

//	void chassisTimeoutHandle(String msgId); // sentDrop is not a must for both client and server, only at client
}
public interface ServerHandler<InChargeMsg extends ChassisMgrableMsg, CoRes extends ChassisMgrableMsg, CtxResponse extends ChassisReplyContext<InChargeMsg, CoRes>>
	extends Handler<CtxResponse>, DropableHandler<InChargeMsg>, ChassisServerHandler<InChargeMsg, CtxResponse> {
}
