/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.codec.io;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;
import io.netty.util.internal.ObjectUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author vttek
 */
public class Http2ControlFrameLimitEncoder extends DecoratingHttp2ConnectionEncoder {

    private static final Logger logger = LogManager.getLogger(Http2ControlFrameLimitEncoder.class);

    private final int maxOutstandingControlFrames;
    private int outstandingControlFrames;
    private final ChannelFutureListener outstandingControlFramesListener = future -> outstandingControlFrames--;
    private Http2LifecycleManager lifecycleManager;
    private boolean limitReached;

    public Http2ControlFrameLimitEncoder(Http2ConnectionEncoder delegate, int maxOutstandingControlFrames) {
        super(delegate);
        this.maxOutstandingControlFrames = ObjectUtil.checkPositive(maxOutstandingControlFrames,
                "maxOutstandingControlFrames");
    }

    @Override
    public void lifecycleManager(Http2LifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
        super.lifecycleManager(lifecycleManager);
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        ChannelPromise newPromise = handleOutstandingControlFrames(ctx, promise);
        return newPromise == null ? promise : super.writeSettingsAck(ctx, newPromise);
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, boolean ack, long data, ChannelPromise promise) {
        // Only apply the limit to ping acks.
        if (ack) {
            ChannelPromise newPromise = handleOutstandingControlFrames(ctx, promise);
            return newPromise == null ? promise : super.writePing(ctx, ack, data, newPromise);
        }
        return super.writePing(ctx, ack, data, promise);
    }

    @Override
    public ChannelFuture writeRstStream(
            ChannelHandlerContext ctx, int streamId, long errorCode, ChannelPromise promise) {
        ChannelPromise newPromise = handleOutstandingControlFrames(ctx, promise);
        return newPromise == null ? promise : super.writeRstStream(ctx, streamId, errorCode, newPromise);
    }

    private ChannelPromise handleOutstandingControlFrames(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!limitReached) {
            if (outstandingControlFrames == maxOutstandingControlFrames) {
                // Let's try to flush once as we may be able to flush some of the control frames.
                ctx.flush();
            }
            if (outstandingControlFrames == maxOutstandingControlFrames) {
                limitReached = true;
                Http2Exception exception = Http2Exception.connectionError(Http2Error.ENHANCE_YOUR_CALM,
                        "Maximum number %d of outstanding control frames reached", maxOutstandingControlFrames);
                logger.info("Maximum number {} of outstanding control frames reached. Closing channel {}",
                        maxOutstandingControlFrames, ctx.channel(), exception);

                // First notify the Http2LifecycleManager and then close the connection.
                lifecycleManager.onError(ctx, true, exception);
                ctx.close();
            }
            outstandingControlFrames++;

            // We did not reach the limit yet, add the listener to decrement the number of outstanding control frames
            // once the promise was completed
            return promise.unvoid().addListener(outstandingControlFramesListener);
        }
        return promise;
    }
}
