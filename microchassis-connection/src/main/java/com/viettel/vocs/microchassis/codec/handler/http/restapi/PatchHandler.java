package com.viettel.vocs.microchassis.codec.handler.http.restapi;

public interface PatchHandler<ReplyCtx> {
	void patchHandle(ReplyCtx serverCtx);
}
