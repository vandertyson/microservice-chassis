package com.viettel.vocs.microchassis.serviceRegistry.codec;


import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import com.viettel.vocs.microchassis.serviceRegistry.codec.context.LbrContext;
import com.viettel.vocs.microchassis.serviceRegistry.codec.handler.LbrHandler;
import com.viettel.vocs.microchassis.tcp.codec.TCIDecoder;
import com.viettel.vocs.microchassis.tcp.codec.MsgHeader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tiennn18
 */

public class LbrCIDecoder extends TCIDecoder<LBRMsg, LbrHandler> {
	public LbrCIDecoder(EventHandler EventHandler, LbrHandler handlers, Map<String, CompletableFuture> syncPromiseMap, ClientConfiguration config, AtomicReference<PeerCounter> counterRef, AtomicReference<ConnectionMode> connModeRef, MsgCounter msgCounter) {
		super(EventHandler, handlers, syncPromiseMap, config, counterRef, connModeRef, msgCounter);
	}

	@Override
	protected LBRMsg createDecodedMsg(ByteBuf body, String url, String messageId, MsgHeader headers) {
		return new LBRMsg(body, url, messageId, headers);
	}

	@Override
	public LbrContext wrapCtx(ChannelHandlerContext ctx, LBRMsg msg) {
		return new LbrContext(ctx, msg);
	}
}
