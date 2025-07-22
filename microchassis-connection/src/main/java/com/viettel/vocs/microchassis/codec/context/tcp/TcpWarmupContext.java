package com.viettel.vocs.microchassis.codec.context.tcp;

import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.buffer.ByteBufAllocator;

public class TcpWarmupContext extends TcpBasedWarmupContext<Msg>{
	public TcpWarmupContext(ByteBufAllocator allocator, Msg req) {
		super(allocator, req);
	}

	public TcpWarmupContext(ByteBufAllocator allocator, Msg req, int bufInitSize) {
		super(allocator, req, bufInitSize);
	}

	@Override
	public Msg newCoMsg(int outInitSize, String path, String msgId) {
		return new Msg(outBufAllocator, outInitSize, path, msgId);
	}

	@Override
	public Msg newCoMsg(String path, String msgId) {
		return new Msg(outBufAllocator, path, msgId);
	}
}
