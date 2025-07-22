package com.viettel.autotest.microchassis.connection.config;

public class ClientConfiguration extends com.viettel.vocs.microchassis.connection.config.ClientConfiguration implements AutoTestClientConfig {
	public boolean sendSync = true; // default is false

	@Override
	public boolean isSendSync() {
		return sendSync;
	}
}
