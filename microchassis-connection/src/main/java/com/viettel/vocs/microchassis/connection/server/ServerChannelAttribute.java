package com.viettel.vocs.microchassis.connection.server;

import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.concurrent.atomic.AtomicReference;

//
///**
// *
// * @author vttek
// */
public class ServerChannelAttribute {
	public static final AttributeKey<String> attrClientVdu = AttributeKey.newInstance("server.channel.client.vdu");

	protected ServerChannelAttribute(){}
	public static NettyServer getServer(Channel c) {
		return c != null ? c.attr(serverRef).get() : null; // might be null if channel died
	}
	public static final AttributeKey<NettyServer> serverRef = AttributeKey.newInstance("server.server");

	public static final AttributeKey<AtomicReference<PeerCounter>> counter  = AttributeKey.newInstance("server.counter");
}
