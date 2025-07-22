package com.viettel.vocs.microchassis.tcp.client;

import com.viettel.vocs.microchassis.codec.handler.tcp.TcpBasedHandler;
import com.viettel.vocs.microchassis.connection.client.InstanceClient;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.tcp.codec.Msg;

public abstract class TcpBasedInstanceClient<BiDMsg extends Msg, Conf extends ClientConfiguration, CHandler extends TcpBasedHandler>
	extends InstanceClient<BiDMsg, BiDMsg, Conf, CHandler> {

	public abstract BiDMsg createReq(byte[] data, String path, String msgId);
	public abstract BiDMsg createReq(byte[] data, String path);
	protected TcpBasedInstanceClient(Conf config, CHandler handlers) {
		super(config, handlers);
	}
}
