
package com.viettel.vocs.microchassis.codec.handler;

import com.viettel.vocs.microchassis.codec.context.ClientReceiveContext;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;

/**
 * here is class for child class only need to define handler it used
 * 	everything here do nothing
 * @param <Req>
 * @param <Res>
 * @param <ReceiveCtx>
 */
public interface ClientReceiveHandler<Res extends ChassisMgrableMsg, Req extends ChassisMgrableMsg, ReceiveCtx extends ClientReceiveContext<Res>>
	extends AsyncHandler<ReceiveCtx> {

	void chassisHandle(ReceiveCtx ctx);

	void chassisDropHandle(Req ctx, boolean isSent);

	void chassisTimeoutHandle(String msgId);
}
