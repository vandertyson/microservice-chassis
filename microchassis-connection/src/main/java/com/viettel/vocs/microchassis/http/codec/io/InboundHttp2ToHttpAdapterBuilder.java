/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.codec.io;

import io.netty.handler.codec.http2.AbstractInboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.util.internal.UnstableApi;

/**
 *
 * @author vttek
 */
@UnstableApi
public final class InboundHttp2ToHttpAdapterBuilder
        extends AbstractInboundHttp2ToHttpAdapterBuilder<InboundHttp2ToHttpAdapter, InboundHttp2ToHttpAdapterBuilder> {

    /**
     * Creates a new {@link InboundHttp2ToHttpAdapter} builder for the specified
     * {@link Http2Connection}.
     *
     * @param connection the object which will provide connection notification
     * events for the current connection
     */
    public InboundHttp2ToHttpAdapterBuilder(Http2Connection connection, int maxContentLength) {
        super(connection);
        maxContentLength(maxContentLength);
        propagateSettings(true);
    }
    public static InboundHttp2ToHttpAdapter build(Http2Connection connection, int maxContentLength){
        return new InboundHttp2ToHttpAdapterBuilder(connection, maxContentLength).build();
    }

    @Override
    protected InboundHttp2ToHttpAdapter build(Http2Connection connection,
            int maxContentLength,
            boolean validateHttpHeaders,
            boolean propagateSettings) throws Exception {
        return new InboundHttp2ToHttpAdapter(connection, maxContentLength, validateHttpHeaders, propagateSettings);
    }
}
