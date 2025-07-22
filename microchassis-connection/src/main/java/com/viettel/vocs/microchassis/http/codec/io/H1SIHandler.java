/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.viettel.vocs.microchassis.http.codec.io;

import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ServerCounter;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.metrics.InternalMetric;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * HTTP1 handler only, no upgrade
 */
public class H1SIHandler extends HSIHandler {
//	protected final HttpServerHandler handler;

	public H1SIHandler(EventHandler eventHandler, HttpServerHandler handler, ServerCounter counter, ServerLimiter limiter, MsgCounter msgCounter) {
		super(eventHandler, handler, counter, limiter, msgCounter);
//		this.h1handler = (HttpServerHandler) handler.get(ChassisConst.SupportVersion.HTTP1_1);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fMsg) throws Exception {
		msgCounter.stack(fMsg);
		if (logger.isDebugEnabled())
			logger.debug("Server receive Http1 msg {} {} of type {} headers {}", fMsg.getClass().getSimpleName(), fMsg.method(), fMsg.uri(), fMsg.headers());
		HttpRequest req = new HttpRequest(fMsg);
		if (logger.isDebugEnabled()) logger.debug("Http1Request  content {}", req.toStringUTF8());
		NettyServer.serverMetric.incr(InternalMetric.Server.COUNT_HTTP1_REQUEST_RECEIVE);
		req.retain();
		httpDecode(ctx, req);
//		if(req.refCnt() == 0) req.retain();
	}

//	@Override
//	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//		if(!(cause instanceof IllegalReferenceCountException)) super.exceptionCaught(ctx, cause);
//	}
}
