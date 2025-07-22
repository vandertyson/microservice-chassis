package com.viettel.vocs.microchassis.http.codec.io;

import com.viettel.vocs.common.datatype.DataUnit;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.connection.MsgCountable;
import com.viettel.vocs.microchassis.connection.client.ClientChannelAttribute;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.http.client.HttpClientConnection;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponse;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * @author vttek
 * HCI<What>Handler is HTTP Client Inbound Handler for <What>
 *   What is Response or Request
 *   H1CI for HTTP1
 *   H2CI for HTTP2
 */
public abstract class HCIHandler extends SimpleChannelInboundHandler<io.netty.handler.codec.http.HttpResponse> implements MsgCountable {
	@Getter
	protected final HttpClientHandler handler;
	protected final EventHandler eventHandler;
	private final MsgCounter msgCounter;
	protected HCIHandler(@NotNull HttpClientHandler handler, EventHandler eventHandler, @NotNull MsgCounter msgCounter) {
		this.eventHandler = eventHandler;
		this.handler = handler;
		this.msgCounter = msgCounter;
	}

	@Override
	public long getMsgCount() {
		return msgCounter.getMsgCount();
	}

	@Override
	public double getBytesCount(DataUnit unit) {
		return msgCounter.getBytesCount(unit);
	}

	protected <T extends io.netty.handler.codec.http.FullHttpResponse> T stack(T res) {
		msgCounter.stack(res);
		return res;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, HttpResponse httpResponse) {
		HttpClientConnection connection = ClientChannelAttribute.getConnection(ctx);
		connection.responseAndAck(ctx, (com.viettel.vocs.microchassis.http.codec.HttpResponse) httpResponse);
	}
}
