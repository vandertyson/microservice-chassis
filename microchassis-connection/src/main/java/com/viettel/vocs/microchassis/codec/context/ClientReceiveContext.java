package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;

public interface ClientReceiveContext<InRes extends ChassisMgrableMsg>
	extends NoReplyContext<InRes> {
	@Override
	InRes getInMsg();

}
