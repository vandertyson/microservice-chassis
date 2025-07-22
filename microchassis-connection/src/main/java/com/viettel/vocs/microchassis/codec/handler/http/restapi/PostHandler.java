package com.viettel.vocs.microchassis.codec.handler.http.restapi;

public interface PostHandler<ReplyCtx> {
	void postHandle(ReplyCtx serverCtx);
}
