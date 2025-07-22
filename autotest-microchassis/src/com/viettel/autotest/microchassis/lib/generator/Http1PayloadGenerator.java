package com.viettel.autotest.microchassis.lib.generator;

import com.viettel.autotest.microchassis.connection.config.GeneratorConfig;
import com.viettel.autotest.microchassis.ChassisAutotestConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.HttpConversionUtil;

public class Http1PayloadGenerator extends FakeTPSGenerator<HttpRequest, ByteBufAllocator> implements PayloadGenerator<HttpRequest, ByteBufAllocator> {
	public Http1PayloadGenerator(GeneratorConfig config, ByteBufAllocator bytebufAllocator) {
		super(config, bytebufAllocator);
	}

	public HttpRequest generateRandomBinaryRequest(ByteBuf content) {
		long createTime = TimeUtils.nowNano();
		HttpRequest request = new HttpRequest(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, testUrl, content));
		request.setMessageId(String.valueOf(ChassisAutotestConfig.reqIDstart.get() + reqID.incrementAndGet()));
		request.setDecodeTime(createTime);
		request.setSentTime(createTime);
		request.headers().set("queueTime", "0");
		request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP);
		request.headers().set("processTime", "0");
		return request;
	}

}
