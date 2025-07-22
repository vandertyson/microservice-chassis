package com.viettel.vocs.microchassis.codec.handler.http.restapi;

import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

public interface RestHandler<ReplyCtx extends HttpServerContext> extends
	DeleteHandler<ReplyCtx>,
	GetHandler<ReplyCtx>,
	PutHandler<ReplyCtx>,
	PostHandler<ReplyCtx>,
	PatchHandler<ReplyCtx>,
	Handler<ReplyCtx> {
	default void dispatch(ReplyCtx serverCtx) {
		try {
			if (serverCtx != null) {
				HttpMethod reqMethod = serverCtx.getRequestMethod();
				if (reqMethod == HttpMethod.GET) getHandle(serverCtx);
				else if (reqMethod == HttpMethod.PUT) putHandle(serverCtx);
				else if (reqMethod == HttpMethod.POST) postHandle(serverCtx);
				else if (reqMethod == HttpMethod.PATCH) patchHandle(serverCtx);
				else if (reqMethod == HttpMethod.DELETE) deleteHandle(serverCtx);
				else defaultHandle(serverCtx);
				// khong duoc release o day vi neu release se lam mat ban bin write to wire
			}
		} catch (Exception ex) {
			serverErrorHandle(serverCtx, ex);
		}
	}

	void serverErrorHandle(ReplyCtx serverCtx, Exception ex);

	void handle(ReplyCtx serverCtx);

	void log(ReplyCtx serverCtx);

	default void notSupportMethodHandle(ReplyCtx serverCtx) {
		log(serverCtx);
		serverCtx.send("Method Not Allowed", HttpResponseStatus.METHOD_NOT_ALLOWED);
	}

	default void defaultHandle(ReplyCtx serverCtx) {
		log(serverCtx);
		notSupportMethodHandle(serverCtx);
	}

	default void postHandle(ReplyCtx serverCtx) {
		log(serverCtx);
		serverCtx.send(serverCtx.getInData());
	}

	default void patchHandle(ReplyCtx serverCtx) {
		log(serverCtx);
		serverCtx.send(serverCtx.getInData());
	}

	default void deleteHandle(ReplyCtx serverCtx) {
		log(serverCtx);
		serverCtx.send(serverCtx.getInData());
	}

	default void getHandle(ReplyCtx serverCtx) {
		log(serverCtx);
		serverCtx.send(serverCtx.getInData());
	}

	default void putHandle(ReplyCtx serverCtx) {
		log(serverCtx);
		serverCtx.send(serverCtx.getInData());
	}
}
