package com.viettel.vocs.microchassis.tcp.client;

import com.viettel.vocs.microchassis.tcp.codec.Msg;

public interface TcpPeer<BiMsg extends Msg> {
	BiMsg createReq(byte[] data, String path, String msgId);

	BiMsg createReq(byte[] data, String path);

}
