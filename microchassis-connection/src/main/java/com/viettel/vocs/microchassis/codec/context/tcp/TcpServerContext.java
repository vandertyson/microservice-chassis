package com.viettel.vocs.microchassis.codec.context.tcp;

import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.codec.context.ServerContext;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * @author tiennn18
 */
public interface TcpServerContext<Type extends Msg> extends ServerContext<Type, Type> {
	default void sendDrop(GenericFutureListener<ChannelFuture>... futures){
		getCoMsg().reuse(MsgType.DROP); // -> client -> BiMsgDecoder -> handleAsync -> sentDropHandler
		if (validateCtx()) send(futures);
	}
	@Override
	default String getCoPath() {
		return getCoMsg().getUrl();
	}
	@Override
	Type getCoMsg(); // need this for generic usages avoid ChassisMgrableMsg

	@Override
	Type getInMsg(); // need this for generic usages avoid ChassisMgrableMsg
	default void echo() {  // only aplly for BiD, copy out from incoming
		dumpToCo(getInMsg()); // deep copy so if deallocated incoming, we still have outgoing
	}
	@Override
	default void send(HttpResponseStatus statusCode, GenericFutureListener<ChannelFuture>... futures) {
		getCoMsg().headers().setStatus(statusCode);
		send(futures);
	}
}
