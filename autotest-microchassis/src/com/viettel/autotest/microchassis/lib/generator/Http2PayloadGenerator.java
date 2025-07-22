package com.viettel.autotest.microchassis.lib.generator;

import com.viettel.autotest.microchassis.connection.config.GeneratorConfig;
import com.viettel.autotest.microchassis.ChassisAutotestConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.HttpConversionUtil;

public class Http2PayloadGenerator extends FakeTPSGenerator<HttpRequest, ChannelHandlerContext> implements PayloadGenerator<HttpRequest, ChannelHandlerContext> {
	public Http2PayloadGenerator(GeneratorConfig config, ByteBufAllocator allocator) {
		super(config, allocator);
	}

	public HttpRequest generateRandomBinaryRequest(ByteBuf content) {
		long createTime = TimeUtils.nowNano();
		HttpRequest request = new HttpRequest(getAllocator(), HttpMethod.POST, testUrl);
		request.writeFrom(content);
		request.setMessageId(String.valueOf(ChassisAutotestConfig.reqIDstart.get() + reqID.incrementAndGet()));
		request.setDecodeTime(createTime);
		request.setSentTime(createTime);
		request.headers().set("queueTime", "0");
		request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP.toString());
		request.headers().set("processTime", "0");
		return request;
	}
}
