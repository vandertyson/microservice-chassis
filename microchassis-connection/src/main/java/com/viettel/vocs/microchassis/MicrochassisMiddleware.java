package com.viettel.vocs.microchassis;

import com.viettel.vocs.microchassis.connection.client.NettyClient;

public class MicrochassisMiddleware<Client extends NettyClient> {
	public final Client serviceClient;

	public MicrochassisMiddleware(Client serviceClient) {
		this.serviceClient = serviceClient;
	}
}
