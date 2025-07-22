package com.viettel.vocs.microchassis.codec.context.http;

import com.viettel.vocs.microchassis.codec.context.CtxHolder;
import io.netty.channel.Channel;

public interface HttpContext {
	boolean validateCtx();
	Channel getChannel();
	default boolean isHttp1(){
		return validateCtx() && CtxHolder.isHttp1(getChannel());
	}
	default boolean isHttp2(){
		return validateCtx() && CtxHolder.isHttp2(getChannel());
	}
}
