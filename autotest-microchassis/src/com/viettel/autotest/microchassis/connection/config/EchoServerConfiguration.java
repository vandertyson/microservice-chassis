package com.viettel.autotest.microchassis.connection.config;

public class EchoServerConfiguration extends com.viettel.vocs.microchassis.connection.config.ServerConfiguration {
	public EchoServerConfiguration() {
	}
	public EchoServerConfiguration(String serverId) {
		super(serverId);
	}

	public EchoServerConfiguration(String id, int port, String lbrId, int maxOpenConnection, int minOpenConnection, int maxHostConnectable, String limitConnectionMode) {
		super(id, port, lbrId, maxOpenConnection, minOpenConnection, maxHostConnectable, limitConnectionMode);
	}

	public EchoServerConfiguration(String id, int port) {
		super(id, port);
	}

	public EchoServerConfiguration(String id, int port, String lbrId) {
		super(id, port, lbrId);
	}
}
