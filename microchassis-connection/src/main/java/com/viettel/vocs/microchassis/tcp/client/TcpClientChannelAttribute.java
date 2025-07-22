package com.viettel.vocs.microchassis.tcp.client;

import com.viettel.vocs.microchassis.connection.client.ClientChannelAttribute;
import com.viettel.vocs.microchassis.tcp.codec.TCIDecoder;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author tiennn18
 */
public interface TcpClientChannelAttribute extends ClientChannelAttribute {
	AttributeKey<TCIDecoder> msgDecoder = AttributeKey.newInstance("client.msgDecoder");

	static TCIDecoder getDecoder(Channel c) {
		return c != null ? c.attr(TcpClientChannelAttribute.msgDecoder).get() : null;
	}

	AttributeKey<Map<String, CompletableFuture>> promiseMap = AttributeKey.newInstance("client.tcp.promise.map");

	static Map<String, CompletableFuture> getPromiseMap(Channel c){
		return c != null ? c.attr(promiseMap).get() : null;
	}
}
