/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.codec.io;

import com.viettel.vocs.common.os.TimeUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author vttek
 */
public class InboundHttp2ToHttpAdapter extends io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter {

    private static final Logger logger = LogManager.getLogger(InboundHttp2ToHttpAdapter.class);
    AtomicLong lastReceivePing = new AtomicLong(0);
    public long lastPing() {return lastReceivePing.get();}
    private boolean isGoAway = false;

    public boolean isGoAway() {
        return isGoAway;
    }

    public InboundHttp2ToHttpAdapter(Http2Connection connection, int maxContentLength, boolean validateHttpHeaders, boolean propagateSettings) {
        super(connection, maxContentLength, validateHttpHeaders, propagateSettings);
    }

    @Override
    protected void onRstStreamRead(Http2Stream stream, FullHttpMessage msg) {
        logger.warn("onRstStreamRead streamid={} msg={}", stream.id(), msg);
        super.onRstStreamRead(stream, msg); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        logger.warn("onRstStreamRead {} errorCode {}", streamId, errorCode);
        super.onRstStreamRead(ctx, streamId, errorCode); //To change body of generated methods, choose Tools | Templates.        
    }


    @Override
    public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
        logger.warn("onGoAwayReceived lastStreamId: {} errorCode {} debugData {}", lastStreamId, errorCode, debugData);
        isGoAway = true;
        super.onGoAwayReceived(lastStreamId, errorCode, debugData); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
        logger.warn("onGoAwaySent lastStreamId: {} errorCode {} debugData {}", lastStreamId, errorCode, debugData);
        super.onGoAwaySent(lastStreamId, errorCode, debugData); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
        logger.warn("onGoAwayRead lastStreamId={} errorCode={} debugData={}", lastStreamId, errorCode, debugData);
        super.onGoAwayRead(ctx, lastStreamId, errorCode, debugData); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("onPingAckRead ctx={} data={}", ctx.channel(), data);
        }
        super.onPingAckRead(ctx, data); //To change body of generated methods, choose Tools | Templates.
        long now = TimeUtils.nowNano();
        if(lastReceivePing.get() < now) lastReceivePing.set(now);
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("onPingRead ctx={} data={}", ctx.channel(), data);
        }
        super.onPingRead(ctx, data);
        long now = TimeUtils.nowNano();
        if(lastReceivePing.get() < now) lastReceivePing.set(now);
    }

}
