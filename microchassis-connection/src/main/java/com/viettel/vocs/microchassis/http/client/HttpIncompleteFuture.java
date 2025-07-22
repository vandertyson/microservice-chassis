package com.viettel.vocs.microchassis.http.client;

import org.apache.commons.lang3.tuple.MutablePair;

import java.util.concurrent.CompletableFuture;

public abstract class HttpIncompleteFuture<ResolvePack> extends MutablePair<Long /*sendTime*/, CompletableFuture<ResolvePack>> {
	public Long getSentTime() {
		return getLeft();
	}
	public CompletableFuture<ResolvePack> getFuture(){
		return getRight();
	}
	public HttpIncompleteFuture(){ // for unknown sentTime
		this(0L);
	}
	public HttpIncompleteFuture(long sentTime){
		setLeft(sentTime);
		setRight(new CompletableFuture<>());
	}

	public boolean cancel(boolean b) {
		try {
			return getFuture().cancel(b);
		}catch (Throwable e){
			return false;
		}
	}
}

