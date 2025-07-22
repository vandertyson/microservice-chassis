package com.viettel.vocs.microchassis.http.client;

import io.netty.channel.Channel;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class HttpChannelPromiseMap<ResolvePack> extends ConcurrentHashMap<String, HttpIncompleteFuture<ResolvePack>> {

}
