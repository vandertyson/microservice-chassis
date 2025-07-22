package com.viettel.autotest.microchassis.lib.generator;

import com.viettel.autotest.microchassis.connection.config.GeneratorConfig;
import com.viettel.autotest.microchassis.ChassisAutotestConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;


public class TcpPayloadGenerator extends FakeTPSGenerator<Msg, ByteBufAllocator> implements PayloadGenerator<Msg, ByteBufAllocator> {

	public TcpPayloadGenerator(GeneratorConfig config, ByteBufAllocator allocator) {
		super(config, allocator);
	}

	public Msg generateRandomBinaryRequest(ByteBuf content) {
		long createTime = TimeUtils.nowNano();
		Msg request = new Msg(content, testUrl);
		request.setMessageId(String.valueOf(ChassisAutotestConfig.reqIDstart.get() + reqID.incrementAndGet()));
		request.setDecodeTime(createTime);
		request.setSentTime(createTime);
		request.headers().set("queueTime", "0");
		request.headers().set("processTime", "0");
		return request;
	}

}
