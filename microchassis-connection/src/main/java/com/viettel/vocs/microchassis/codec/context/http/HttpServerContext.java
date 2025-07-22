/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.codec.context.http;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.codec.context.ChassisReplyContext;
import com.viettel.vocs.microchassis.codec.context.CtxHolder;
import com.viettel.vocs.microchassis.codec.context.ServerContext;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.client.HttpChannelAttribute;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.metrics.InternalMetric;
import com.viettel.vocs.microchassis.metrics.Metric;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;

/**
 * Http la loai goi tin dac biet bat buoc co response
 * se co 3 loai:
 * - single: 1 request 1 response, client gui toi server
 * - multi: 1 request n responses, client gui toi server
 * - mock: 0 request, server trigger fireChannelRead fake de send response tren channel, server gui toi client
 * mock only available by http2, inherit from single and multi request
 * khong co tinh 2 chieu, neu server muon gui chu dong den client ma khong co connection thi phai call to HTTPServer khac cua clientPeer
 * hoac bat buoc dung mockRequest
 * <p>
 * This class equivalent to HttpSingleContext
 */
public class HttpServerContext extends ChassisReplyContext<HttpRequest, HttpResponse>
        implements ServerContext<HttpRequest, HttpResponse>, HttpContext { // for MockHttp2Req and SingleHttp2Req and multiH2response

    @Override
    public boolean validateCtx() {
        return super.validateCtx();
    }

    @Override
    public Channel getChannel() {
        return super.getChannel();
    }

    private static final Logger logger = LogManager.getLogger(HttpServerContext.class);
    public final int streamId = inChargedMsg.getStreamId(); // at server it came from 2 (client from 1) 0=no use
    @Getter
    private final boolean isHttp2 = streamId > 0;

    private static final boolean H2_DOUBLE_FLUSH = StringUtils.isEmpty(System.getenv("DISABLE_H2_DOUBLE_FLUSH"));


    /**
     * with server, outgoing is response, incoming is request
     */
    @Override
    public void send(HttpResponse srcMsg, GenericFutureListener<ChannelFuture>... futures) {
        dumpToCo(srcMsg);
        send(futures);
    }

    public HttpServerContext enqueueRetain() {
        inChargedMsg.retain();
        coMsg.retain();
        return this;
    }

    public void sendDrop(GenericFutureListener<ChannelFuture>... futures) {
//		getCoMsg().reuse(MsgType.DROP);
        send(HttpResponseStatus.UNPROCESSABLE_ENTITY);
    }

//	public static HttpServerContext wrapCtx(ChannelHandlerContext ctx, FullHttpRequest msg, long sentTime) {
//		HttpServerContext httpServerContext = wrapCtx(ctx, msg);
//		httpServerContext.getInMsg().setSentTime(sentTime);
//		return httpServerContext;
//	}

//	public static HttpServerContext wrapCtx(ChannelHandlerContext ctx, FullHttpRequest msg) {
//		return new HttpServerContext(ctx, msg);
//	}

    /**
     * Stream Creation: New streams are created for each HTTP request-response pair, and they are assigned a unique stream ID.
     * Stream IDs are 32-bit integers, with the lowest bit indicating whether the stream is initiated by the client (even) or the server (odd).
     */
    protected final Http2ConnectionEncoder encoder = HttpChannelAttribute.getEncoder(ctx.channel());

    protected boolean keepAlive;

    @Override
    public final String getCoPath() {
        return getInPath(); // corresponding reply
    }


    @Override
    public final MsgType getCoMsgType() {
        return null; //HTTP dont have mgr type
    }

    @Override
    public final boolean isCoMgr() {
        return false; //HTTP dont have mgr type
    }

    @Override
    public HttpResponse newCoMsg(int outInitSize, String path, String msgId) {
        return new HttpResponse(outBufAllocator, outInitSize, msgId);
    }

    @Override
    public HttpResponse newCoMsg(String path, String msgId) {
        return new HttpResponse(outBufAllocator, msgId);
    }

    public HttpServerContext(ChannelHandlerContext ctx, FullHttpRequest msg) {
        this(ctx, new HttpRequest(msg));
    }

    public HttpServerContext(ChannelHandlerContext ctx, HttpRequest msg) {
        super(ctx, msg);
        NettyServer.serverMetric.incr(InternalMetric.Server.COUNT_HTTP1_REQUEST_RECEIVE);
        this.keepAlive = HttpUtil.isKeepAlive(inChargedMsg);
        inChargedMsg.onHeaderNotNull(ChassisConst.CustomHeader.msgIdHeader, messId ->
                coMsg.headers().set(ChassisConst.CustomHeader.msgIdHeader, messId));
    }

    @Override
    public String getInPath() {
        return inChargedMsg.uri();
    }

    public HttpMethod getRequestMethod() {
        return inChargedMsg.method();
    }


    @Override
    public final void dumpToCo(HttpResponse response) {
        coMsg.copyAll(response);
    }

    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        super.operationComplete(future);
        if (!keepAlive)
            NettyServer.serverMetric.incr(InternalMetric.Server.COUNT_HTTP1_RESPONSE_FLUSH_SUCCESS);
    }

    private boolean prepareSend(AtomicReference<GenericFutureListener<ChannelFuture>[]> futures) {
        if (coMsg.isSent()) {
            if (logger.isDebugEnabled())
                logger.warn("Stream id {} sent response at {} ms ago", streamId, TimeUtils.miliPassed(coMsg.getSentTime()));
            return false;
        } else if (!validateCtx() || !ctx.channel().isOpen() || !ctx.channel().isActive()) {
            if (logger.isDebugEnabled()) {
                logger.debug("[Client channel is not active. Can not send response]{ctx={}, channel={}}", ctx, validateCtx() ? ctx.channel() : null);
            }
            return false;
        }
        inChargedMsg.onHeader(ChassisConst.CustomHeader.msgIdHeader, idHeader -> {
            if (idHeader != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Got mess_id {} . Set in response headers", idHeader);
                }
                coMsg.headers().set(ChassisConst.CustomHeader.msgIdHeader, idHeader);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("No request ID. Sending non mess_id response.");
                }
            }
        });

        if (inChargedMsg.getMessageId() != null && !inChargedMsg.getMessageId().isEmpty())
            coMsg.setMessageId(inChargedMsg.getMessageId());
        HttpHeaders coHeaders = coMsg.headers();
        coHeaders.setInt(CONTENT_LENGTH, coMsg.readableBytes());
//		futures.add(this); // included outgoing
        if (keepAlive) {
            if (HttpVersion.HTTP_1_1.equals(coMsg.protocolVersion()))
                coHeaders.set(CONNECTION, KEEP_ALIVE);
        } else {
            coHeaders.set(CONNECTION, CLOSE);
            futures.set(CtxHolder.addListeners(futures.get(),
                    ChannelFutureListener.CLOSE,
                    f -> NettyServer.serverMetric.incr(InternalMetric.Server.COUNT_HTTP1_RESPONSE_SEND)));
        }
        ByteBuf readOnlyContentByteBuf = coMsg.getReadOnlyContentByteBuf();
        if (checkSumEngine != null) coMsg.headers().add(
                checkSumEngine.getHttpHeaderKey(),
                checkSumEngine.hash(readOnlyContentByteBuf)
        );
        return true;
    }

    @Override
    public void send(GenericFutureListener<ChannelFuture>... futures) {
        AtomicReference<GenericFutureListener<ChannelFuture>[]> atomicReference = new AtomicReference<>(futures);
        if (!validateCtx() || !ctx.channel().isActive() || !ctx.channel().isOpen()
                || coMsg.isSent()
                || !prepareSend(atomicReference)
        ) { // sent, so no resend
            try {
                if (futures != null) newChannelPromiseThenResolve(futures);
            } catch (Exception ignored) {
            }
            return;
        }

        // check validate context roi
        GenericFutureListener<ChannelFuture>[] futureListeners = CtxHolder.addListeners(
                atomicReference.get(),
                future -> {
                    Metric serverMetric = NettyServer.serverMetric;
                    serverMetric.incr(InternalMetric.Server.COUNT_HTTP1_RESPONSE_SEND);
                    if (future.isSuccess()) {
                        serverMetric.incr(InternalMetric.Server.COUNT_HTTP1_RESPONSE_FLUSH_SUCCESS);
                        serverMetric.add(InternalMetric.Server.TOTAL_BYTES_SEND, coMsg.readableBytes());
                    } else serverMetric.incr(InternalMetric.Server.COUNT_HTTP1_RESPONSE_FLUSH_FAIL);
                });

        // set time before fire to avoid concurrent fire triggered
        coMsg.setSentTime();
        // fire now
        if (isHttp1()) super.send(futureListeners); // server do not close ctx, let client close, only release coMsg
        else if (isHttp2()) send2(streamId, futureListeners);
    }

    private void send2(int streamId, GenericFutureListener<ChannelFuture>... futures) {
        /**
         * must carefully ensure the promised stream ID does not conflict with any stream IDs the client might use or has already allocated.
         * How to Determine a Safe Stream ID:
         * - Even-Numbered IDs for Server-Initiated Streams
         * - Server push must always use even-numbered stream IDs.
         * - Stream IDs increment monotonically, and the server must not reuse a stream ID.
         * => encoder.connection().local().incrementAndGetNextStreamId()
         */
        Http2Headers h2headers = coMsg.h2headers();
        try {
            encoder.writeHeaders(ctx, streamId, h2headers, 0, false, ctx.voidPromise());
        } catch (Throwable e) {
            logger.error(e, e);
        }
        if (logger.isDebugEnabled())
            logger.debug(String.format("===============Respond to client|ctx %s|Stream id %d|Content len %d|Headers %s=============== ", ctx.channel(), streamId, coMsg.readableBytes(), h2headers));
        try {
            if (coMsg.getReadOnlyContentByteBuf() == null) {
                logger.error("========================NULL bytebuf {}========================", coMsg.getReadOnlyContentByteBuf());
            }
            ChannelPromise channelPromise = newChannelPromise(futures);
            if (channelPromise == null) {
                logger.error("========================NULL channelPromise========================");
            }
            encoder.writeData(ctx, streamId, coMsg.getReadOnlyContentByteBuf(), 0, true, channelPromise);
        } catch (Throwable e) {
            logger.error(e, e);
        }
        try {
            ctx.flush();
            if (H2_DOUBLE_FLUSH) {
                ctx.channel().flush();//        workaround flush
            }
        } catch (Throwable e) {
//			if (!(e instanceof io.netty.util.IllegalReferenceCountException) && e.getMessage().contains("refCnt: 0"))
            logger.error(e, e);
        }
    }

    @Override
    public void send(HttpResponseStatus statusCode, GenericFutureListener<ChannelFuture>... futures) {
        coMsg.setStatus(statusCode);
        send(futures);
    }
}
