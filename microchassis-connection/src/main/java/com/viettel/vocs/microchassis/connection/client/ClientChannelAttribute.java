/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.client;

import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.MainSite;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ClientLimiter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

import java.util.concurrent.atomic.AtomicReference;

public interface ClientChannelAttribute {
	AttributeKey<ClientConnection> ownerConnection = AttributeKey.newInstance("client.ClientConnection");

	AttributeKey<AtomicReference<ClientLimiter>> limiter  = AttributeKey.newInstance("client.clientLimiter");
	AttributeKey<AtomicReference<ConnectionMode>> activeCM  = AttributeKey.newInstance("client.connectionMode");
	AttributeKey<MainSite> mainSiteRef = AttributeKey.newInstance("client.client");

	static ConnectionMode getConnMode(Channel ch) {
		AtomicReference<ConnectionMode> connModeRef = getConnModeRef(ch);
		return connModeRef != null ? connModeRef.get() : null;
	}
	static AtomicReference<ConnectionMode> getConnModeRef(Channel c) {
		return c != null ? c.attr(ClientChannelAttribute.activeCM).get() : null;
	}
	static ClientLimiter getLimiter(Channel ch) {
		AtomicReference<ClientLimiter> connLimiterRef = getLimiterRef(ch);
		return connLimiterRef != null ? connLimiterRef.get() : null;
	}
	static AtomicReference<ClientLimiter> getLimiterRef(Channel c) {
		return c != null ? c.attr(ClientChannelAttribute.limiter).get() : null;
	}

	static <T extends ClientConnection> T getConnection(ChannelHandlerContext ctx) {
		return ctx !=null ? getConnection(ctx.channel()) : null;
	}
	static <T extends ClientConnection> T getConnection(Channel c) {
		try {
			return (T) (c != null ? c.attr(ownerConnection).get() : null);
		} catch (ClassCastException e) {
			return null;
		}
	}
}
