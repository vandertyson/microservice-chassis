package com.viettel.vocs.microchassis.tcp.client;

import com.viettel.vocs.microchassis.codec.handler.tcp.TcpBasedHandler;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.tcp.codec.Msg;

/**
 * @author vttek
 */
public abstract class TcpBasedClient<BiMsg extends Msg, Conf extends ClientConfiguration, CHandler extends TcpBasedHandler>
	extends NettyClient<BiMsg, BiMsg, Conf, CHandler> implements TcpPeer<BiMsg> {

	protected TcpBasedClient(Conf clientConfig) {
		super(clientConfig);
	}

	public void send(byte[] data, String path) throws Exception {
		// create init request then send from byte
		send(createReq(data, path));
	}
	public void send(byte[] data, String path, String msgId) throws Exception {
		// create init request then send from byte
		send(createReq(data, path, msgId));
	}
}
