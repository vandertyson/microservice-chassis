package com.viettel.vocs.microchassis.http.client;

import java.util.function.BiConsumer;

public class HttpAsyncIncompleteFuture<ResolvePack> extends HttpIncompleteFuture<ResolvePack> {
	final BiConsumer<ResolvePack, Long> callback;

	public HttpAsyncIncompleteFuture(long sentTime, BiConsumer<ResolvePack, Long> callback) {
		super(sentTime);
		this.callback = callback;
	}
}
