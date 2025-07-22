package com.viettel.autotest.microchassis.connection.config;

public class EchoHttpServerConfiguration extends com.viettel.vocs.microchassis.http.config.HttpServerOptions {

	public EchoHttpServerConfiguration() {

	}
	public EchoHttpServerConfiguration(String id, int port) {
		super(id, port);
	}

	public EchoHttpServerConfiguration(String serverId) {
		super(serverId);
	}
}
