package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.client.ChannelAttribute;
import com.viettel.vocs.microchassis.http.codec.io.H1CIHandler;
import com.viettel.vocs.microchassis.http.codec.io.H1SIHandler;
import com.viettel.vocs.microchassis.http.codec.io.H2CIHandler;
import com.viettel.vocs.microchassis.http.codec.io.H2SIHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.GenericFutureListener;

public interface CtxHolder {

	static int getMaxReceiveStreamID(ChannelHandlerContext ctx) {
		return ctx == null ? -1 : getMaxReceiveStreamID(ctx.channel());
	}
	static int getMaxReceiveStreamID(Channel channel) {
		H2CIHandler get = channel.pipeline().get(H2CIHandler.class);
		return get == null ? -1 : get.getMaxReceiveStreamID();
	}
	static boolean isHttp2(ChannelHandlerContext ctx){
		return ctx != null && isHttp2(ctx.channel());
	}
	static boolean isHttp1(Channel ch){
		return ch != null && (
			ch.pipeline().get(H1SIHandler.class) != null
				|| ch.pipeline().get(H1CIHandler.class) != null
		);
	}
	static boolean isHttp2(Channel ch){
		return ch != null && (
			ch.pipeline().get(H2SIHandler.class) != null
			|| ch.pipeline().get(H2CIHandler.class) != null
		);
	}
	static String getIpFrom(Channel channel) {
		Endpoint e = Endpoint.remote(channel);
		return e!= null ? e.getTarget() : null;
	}

	static GenericFutureListener<ChannelFuture>[] addListeners(GenericFutureListener<ChannelFuture>[] listeners, GenericFutureListener<ChannelFuture>... newListeners) {
		int length = listeners == null ? 0 : listeners.length; // null safe
		int addLength = newListeners.length;
		GenericFutureListener<ChannelFuture>[] newArray = new GenericFutureListener[length + addLength];
		if (length > 0) System.arraycopy(listeners, 0, newArray, 0, length);
		System.arraycopy(newListeners, 0, newArray, length, addLength);
		return newArray;
	}

	static boolean validateCtx(ChannelHandlerContext ctx) {
		return ctx != null && ctx.channel() != null;
	}

	static String getIpFrom(ChannelHandlerContext ctx) {
		return getIpFrom(ctx.channel());
	}


	default String getChannelShort() {
		return getChannelShort(getChannel());
	}
	static String getChannelShort(Channel c) {
		return c.id().asShortText();
	}
	default String getChannelString() {
		return getChannel().toString();
	}
	default String getCtxIp() {
		return validateCtx() ? CtxHolder.getIpFrom(getCtx()) : null;
	}

	default Endpoint getEndpoint() {
		return ChannelAttribute.getEndpoint(getChannel());
	}

	ChannelHandlerContext getCtx();

	default boolean validateCtx() {
		return CtxHolder.validateCtx(getCtx());
	}

	default Channel getChannel() {
		return validateCtx() ? getCtx().channel() : null;
	}

	default ChannelPromise newChannelPromiseThenResolve(GenericFutureListener<ChannelFuture>... futures) {
		return newChannelPromise(futures).setSuccess();
	}

	default ChannelPromise newChannelPromise(GenericFutureListener<ChannelFuture>... futures) {
		Channel channel = getChannel();
		return channel !=null
			? (futures != null && futures.length > 0
			? channel.newPromise().addListeners(futures)
			: channel.voidPromise())
			: null;
	}
}

