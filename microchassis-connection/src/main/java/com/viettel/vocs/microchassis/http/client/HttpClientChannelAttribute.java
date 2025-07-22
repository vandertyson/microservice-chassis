package com.viettel.vocs.microchassis.http.client;

import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.http.codec.io.InboundHttp2ToHttpAdapter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public interface HttpClientChannelAttribute extends HttpChannelAttribute {
	AttributeKey<AtomicReference<HttpIncompleteFuture<HttpClientReceiveContext>>> syncFuture = AttributeKey.newInstance("client.future");
	AttributeKey<InboundHttp2ToHttpAdapter> h2toh1Adapter = AttributeKey.newInstance("client.ClientHttp2InboundAdapter");
//	AtomicBoolean syncAccessFuture = new AtomicBoolean();
//	static HttpIncompleteFuture<HttpClientReceiveContext> popFuture(Channel c) {
//		while(!syncAccessFuture.compareAndSet(false, true)) TimeUtils.waitSafeTick();
//		HttpIncompleteFuture<HttpClientReceiveContext> result = c != null ? c.attr(HttpClientChannelAttribute.syncFuture).get().getAndSet(null) : null;
//		syncAccessFuture.set(false); // for other access
//		if(result == null) System.out.println("found null future on channel " + c.id());
//		else System.out.println("found future on channel " + c.id());
//		return result;
//	}
//	static void waitAndSetFuture(Channel c, HttpIncompleteFuture<HttpClientReceiveContext> resFuture) {
//		if(c == null) return;
//		while (/* doan code nay quyet dinh kha nang blocking tren channel http1, chi cho phep 1 ban tin duoc gui tren promise tai 1 thoi diem, khong duoc thay doi */
//			 !c.attr(HttpClientChannelAttribute.syncFuture).get()
//				.compareAndSet(null, resFuture)) TimeUtils.waitSafeTick(); // current thread wait here // if old value present, so put failed -> while(true) come to wait, else, old value null, no one claimed this channel, put success, break while
//		System.out.println("Set f on channel "+ c.id());
//	}

	static InboundHttp2ToHttpAdapter getInboundAdapter(Channel channel) {
		return channel.attr(h2toh1Adapter).get();
	}

	AttributeKey<Map<String /*reqIdent*/, AtomicReference<Pair<String/*msgId*/, HttpIncompleteFuture<HttpClientReceiveContext>>>>> promiseMap = AttributeKey.newInstance("client.http.promise.map");
	AttributeKey<ChannelPromise> settingPromise = AttributeKey.newInstance("client.http.setting.promise");

	static Map<String /*reqIdent*/, AtomicReference<Pair<String/*msgId*/, HttpIncompleteFuture<HttpClientReceiveContext>>>> getPromiseMap(Channel c){ // init always not null
		return c != null ? c.attr(promiseMap).get() : null;
	}
}
