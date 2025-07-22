package com.viettel.vocs.microchassis.http.client;

public class HttpSyncIncompleteFuture<ResolvePack> extends HttpIncompleteFuture<ResolvePack> {
	public HttpSyncIncompleteFuture() {
		super();
	}

	public HttpSyncIncompleteFuture(long sentTime) {
		super(sentTime);
	}
}
