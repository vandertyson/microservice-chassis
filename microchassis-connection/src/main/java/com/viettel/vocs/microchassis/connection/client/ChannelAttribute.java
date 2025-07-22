package com.viettel.vocs.microchassis.connection.client;

import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.concurrent.atomic.AtomicReference;

public interface ChannelAttribute {

	AttributeKey<AtomicReference<PeerCounter>> counter  = AttributeKey.newInstance("peer.counter");
	AttributeKey<Endpoint> connectionEndpoint = AttributeKey.newInstance("conn.endpoint");

	static PeerCounter getCounter(Channel c){
		AtomicReference<PeerCounter> counterRef = getCounterRef(c);
		return counterRef != null ? counterRef.get() : null; // might be null if channel died
	}

	static AtomicReference<PeerCounter> getCounterRef(Channel c) {
		return c != null ? c.attr(counter).get() : null;
	}

	static Endpoint getEndpoint(Channel channel) {
		return channel != null ? channel.attr(connectionEndpoint).get() : null;
	}

	static String getRemoteIp(Channel channel) {
		String ip = getEndpoint(channel).ip;
		return "127.0.0.1".equals(ip) ? "localhost" : ip;
	}
}
