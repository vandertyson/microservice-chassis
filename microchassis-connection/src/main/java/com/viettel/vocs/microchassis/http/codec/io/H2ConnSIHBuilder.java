/*
 * Copyright 2015 The Netty Project
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
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ServerCounter;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import io.netty.handler.codec.http2.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.viettel.vocs.microchassis.http.codec.Http2CodecUtils.CUSTOM_SENSITIVE_HTTP2_HEADERS;
import static io.netty.handler.logging.LogLevel.DEBUG;

/**
 * Http2 server bi (included incoming/req and outgoing/res, due to server's role) handler
 */
public final class H2ConnSIHBuilder extends AbstractHttp2ConnectionHandlerBuilder<H2ConnSIHandler, H2ConnSIHBuilder> {
    private static final Logger logger = LogManager.getLogger(H2ConnSIHBuilder.class);
    private static final Http2FrameLogger frameLogger = new Http2FrameLogger(DEBUG, H2ConnSIHandler.class);
    private final HttpServerHandler http2handler;
    private final HttpServerOptions options;
    private final ServerCounter counter;
    private final ServerLimiter limiter;
    private final EventHandler eventHandler;

    public H2ConnSIHBuilder(EventHandler eventHandler, HttpServerHandler handler, HttpServerOptions options, ServerCounter counter, ServerLimiter limiter) {
//		this.http2handler = (HttpServerHandler) handlers.get(ChassisConst.SupportVersion.HTTP2_0);
        this.http2handler = handler;
        this.options = options;
        this.eventHandler = eventHandler;
        this.limiter = limiter;
        this.counter = counter;
        frameLogger(frameLogger);
//		options.onSupport(ChassisConst.SupportVersion.HTTP_2_0, (Consumer<HttpVersionConfigures>) config ->
        initialSettings(options.http2.makeSetting());
        headerSensitivityDetector(CUSTOM_SENSITIVE_HTTP2_HEADERS);
        logger.info("H2ConnSIHandler constructs headerSensitivityDetector {}", CUSTOM_SENSITIVE_HTTP2_HEADERS);
        autoAckSettingsFrame(true);
        autoAckPingFrame(true);
    }

    @Override
    public H2ConnSIHandler build() {
        return super.build();
    }

    @Override
    protected H2ConnSIHandler build(Http2ConnectionDecoder decoder,
                                    Http2ConnectionEncoder encoder,
                                    Http2Settings initialSettings) {
        H2ConnSIHandler hand = new H2ConnSIHandler(eventHandler, http2handler, options,
                decoder, encoder, initialSettings, limiter, counter);
        headerSensitivityDetector(CUSTOM_SENSITIVE_HTTP2_HEADERS);
        logger.info("H2ConnSIHandler build headerSensitivityDetector {}", CUSTOM_SENSITIVE_HTTP2_HEADERS);
        frameListener(hand);
        return hand;
    }
}
