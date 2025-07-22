package com.viettel.vocs.microchassis.codec.handler;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;

public interface MessageBasedHandler<Msg extends ChassisMgrableMsg> extends AsyncHandler<Msg> {
	/**
	 * kinda callback, based interface for app to create callback on message, that predefine
	 * hendle and timeoutHandle
	 */
}
