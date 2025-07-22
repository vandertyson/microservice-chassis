package com.viettel.vocs.microchassis.tcp.codec;

import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;


public class MsgCIDecoder extends TCIDecoder<Msg, TcpHandler> {

	public MsgCIDecoder(EventHandler eventHandler, TcpHandler handlers, Map<String, CompletableFuture> syncPromiseMap, ClientConfiguration config, AtomicReference<PeerCounter> counter, AtomicReference<ConnectionMode> connMode, MsgCounter msgCounter) {
		super(eventHandler, handlers, syncPromiseMap, config, counter, connMode, msgCounter);
	}

	@Override
	public TcpContext wrapCtx(ChannelHandlerContext ctx, Msg msg) {
		return new TcpContext(ctx, msg);
	}

	@Override
	protected Msg createDecodedMsg(ByteBuf body, String url, String messageId, MsgHeader headers) {
		return new Msg(body, url, messageId, headers);
	}
}

