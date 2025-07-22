package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;

public interface NoReplyNoContext<InChargeMsg extends ChassisMgrableMsg>
	extends MsgHolder<InChargeMsg> {
	/** mostly for client when cannot send
	 */
	@Override
	InChargeMsg getInMsg();

}
