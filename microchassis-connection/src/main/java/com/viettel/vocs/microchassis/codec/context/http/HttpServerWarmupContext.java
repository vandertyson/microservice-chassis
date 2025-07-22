package com.viettel.vocs.microchassis.codec.context.http;

import com.viettel.vocs.microchassis.codec.context.ChassisWarmupContext;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.GenericFutureListener;

public abstract class HttpServerWarmupContext extends ChassisWarmupContext<HttpRequest, HttpResponse> {
	public HttpServerWarmupContext(ByteBufAllocator allocator, HttpRequest req) {
		super(allocator, req);
	}

	public HttpServerWarmupContext(ByteBufAllocator allocator, HttpRequest req, int bufInitSize) {
		super(allocator, req, bufInitSize);
	}

	@Override
	public final String getCoPath() {
		return getInPath(); // corresponding reply
	}
	@Override
	public HttpResponse newCoMsg(String path, String msgId) {
		return new HttpResponse(outBufAllocator, msgId);
	}

	@Override
	public HttpResponse newCoMsg(int outInitSize, String path, String msgId) {
		return new HttpResponse(outBufAllocator, outInitSize, msgId);
	}


	@Override
	public final void dumpToCo(HttpResponse response) {
		coMsg.copyAll(response);
	}

	@Override
	public void send(HttpResponseStatus statusCode, GenericFutureListener<ChannelFuture>... futures) {
		coMsg.setStatus(statusCode);
		send(futures);
	}
}
