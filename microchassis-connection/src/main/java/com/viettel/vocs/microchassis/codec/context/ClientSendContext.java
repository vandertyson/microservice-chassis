package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;

public interface ClientSendContext<InReq extends ChassisMgrableMsg>
	extends NoReplyNoContext<InReq> {
	@Override
	InReq getInMsg();
}
