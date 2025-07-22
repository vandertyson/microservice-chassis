package com.viettel.vocs.microchassis.http.codec;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;

public class HttpMultiResponse extends HttpResponse {
	public HttpMultiResponse(FullHttpResponse msg) {
		super(msg);
	}

	public HttpMultiResponse(ByteBufAllocator allocator, String msgId) {
		super(allocator, msgId);
	}

	public HttpMultiResponse(HttpResponse origin, boolean shareBuffer) {
		super(origin, shareBuffer);
	}
}
