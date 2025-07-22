package com.viettel.vocs.microchassis.codec.context.tcp;

import com.viettel.vocs.microchassis.codec.context.ClientReceiveContext;
import com.viettel.vocs.microchassis.codec.context.ClientSendContext;
import com.viettel.vocs.microchassis.tcp.codec.Msg;

/**
 * @author tiennn18
 */
public interface TcpClientContext<Type extends Msg> extends ClientSendContext<Type>, ClientReceiveContext<Type> {
	@Override
	Type getInMsg(); // need this for generic usages avoid ChassisMgrableMsg

}
