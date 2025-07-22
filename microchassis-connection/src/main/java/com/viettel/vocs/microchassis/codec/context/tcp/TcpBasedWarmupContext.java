package com.viettel.vocs.microchassis.codec.context.tcp;

import com.viettel.vocs.microchassis.codec.context.ChassisWarmupContext;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.buffer.ByteBufAllocator;

public abstract class TcpBasedWarmupContext<BiDMsg extends Msg> extends ChassisWarmupContext<BiDMsg, BiDMsg>
	implements TcpClientContext<BiDMsg>, TcpServerContext<BiDMsg> {

	protected TcpBasedWarmupContext(ByteBufAllocator allocator, BiDMsg request) {
		super(allocator, request);
	}

	protected TcpBasedWarmupContext(ByteBufAllocator allocator, BiDMsg request, int outBufInitSize) {
		super(allocator, request, outBufInitSize);
	}
}
