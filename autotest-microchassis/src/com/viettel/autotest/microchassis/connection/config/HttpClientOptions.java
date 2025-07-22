package com.viettel.autotest.microchassis.connection.config;

public class HttpClientOptions extends com.viettel.vocs.microchassis.http.config.HttpClientOptions implements AutoTestClientConfig {
	public boolean sendSync = true; // default is false

	public HttpClientOptions(String host, int port, String clientID) {
		super(host, port, clientID);
	}

	protected HttpClientOptions() {
		super();
	}
	public HttpClientOptions(String clientId) {
		super(clientId);
	}

	@Override
	public boolean isSendSync() {
		return sendSync;
	}
}
