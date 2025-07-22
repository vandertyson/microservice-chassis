package com.viettel.vocs.microchassis.codec.handler;

public interface DropableHandler<Target> {
	void dropHandle(Target failedReq, boolean isSent); // haven't sent, can retry
}
