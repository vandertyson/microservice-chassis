package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;

public interface NoReplyContext<InChargeMsg extends ChassisMgrableMsg>
	extends NoReplyNoContext<InChargeMsg>, CtxHolder {
	@Override
	InChargeMsg getInMsg();
}
