package com.viettel.vocs.microchassis.codec.handler.tcp;

import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.tcp.codec.Msg;

public class TcpHandler extends TcpBasedHandler<Msg, TcpContext> {
	public TcpHandler(PeerConfig config) {
		super(config);
	}

	@Override
	public void dropHandle(Msg failedReq, boolean isSent) {
		super.dropHandle(failedReq, isSent);
		timeoutHandle(failedReq.getMessageId());
	}
}
