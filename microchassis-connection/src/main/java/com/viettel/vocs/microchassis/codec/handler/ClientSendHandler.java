package com.viettel.vocs.microchassis.codec.handler;

import com.viettel.vocs.microchassis.codec.context.ClientReceiveContext;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;

public interface ClientSendHandler<Req extends ChassisMgrableMsg, Res extends ChassisMgrableMsg, ReceiveCtx extends ClientReceiveContext<Res>>

	extends AsyncHandler<ReceiveCtx>, DropableHandler<Req> /*, SentHandler<Req>*/{

}
