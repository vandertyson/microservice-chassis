package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.common.config.value.BooleanConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.connection.client.ChannelAttribute;
import com.viettel.vocs.microchassis.connection.server.ServerChannelAttribute;
import com.viettel.vocs.microchassis.util.MonitorManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.CompleteFuture;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public interface ReplyContext<InChargeMsg extends ChassisMgrableMsg, CorrespondingMsg extends ChassisMgrableMsg>
        extends NoReplyContext<InChargeMsg> {
    static boolean CGW_REPLY_NEW_THREAD = new BooleanConfig("CGW_REPLY_NEW_THREAD", false).get();

    @Override
    InChargeMsg getInMsg();

    /**
     * for client and server that handle incoming on channel with corresponding reply
     */
    CorrespondingMsg getCoMsg();

    // Co sent msg not monitor for timeout, if monitor -> 3way responsibility
    boolean isCoMgr();

    MsgType getCoMsgType();

    String getCoPath(); // same among req/res/multiRes

    default String getCoID() {
        return getCoMsg().getMessageId();
    }

    default byte[] getCoData() {
        return getCoMsg().getContent();
    }


    //    public abstract  void outRelease(); // khong can ham nay vi outgoing netty da handle release buff roi

    /**
     * set response for this context
     * ReplyContext usually is serverContext, when request is read and process by client,
     * client return clientContext, businessThread will bring clientContext.getInMsg() to some call back handle
     * to create serverResponse -> serverContext.setResponse(serverResponse)
     * BUT SET BY COPY EACH COMPONENT
     * not allow to overwrite precreated outgoing
     *
     * @param: outgoing
     */
    default void dumpToCo(CorrespondingMsg sourceMsg) {
        getCoMsg().copyAll(sourceMsg);
    }

    default void send(CorrespondingMsg srcMsg, GenericFutureListener<ChannelFuture>... futures) {
        dumpToCo(srcMsg);
        send(futures);
    }

    default void send(ByteBuf bb, HttpResponseStatus statusCode, GenericFutureListener<ChannelFuture>... futures) {
        getCoMsg().writeFrom(bb);
        send(statusCode, futures);
    }

    default void send(ByteBuf bb, GenericFutureListener<ChannelFuture>... futures) {
        send(bb, null, futures);
    }

    default void send(byte[] payload, HttpResponseStatus statusCode, GenericFutureListener<ChannelFuture>... futures) {
        getCoMsg().writeFrom(payload);
        send(statusCode, futures);
    }

    default void send(byte[] payload, GenericFutureListener<ChannelFuture>... futures) {
        send(payload, null, futures);
    }

    default void send(String payload, HttpResponseStatus statusCode, GenericFutureListener<ChannelFuture>... futures) {
        send(payload.getBytes(StandardCharsets.UTF_8), statusCode, futures);
    }

    default void send(String payload, GenericFutureListener<ChannelFuture>... futures) {
        send(payload.getBytes(StandardCharsets.UTF_8), null, futures);
    }

    /**
     * send statusCode with handling future, if outgoing has content, it sent, else send empty body with headers and status code
     *
     * @param statusCode
     * @param futures
     */
    void send(HttpResponseStatus statusCode, GenericFutureListener<ChannelFuture>... futures);  // override for different setstatus func


    /**
     * final trigger to send with completed Outgoing content
     * set final mgr header
     * validate outgoing
     * call to sendByChannel to add future for finish send resolve
     * this send function flow do not must add current context
     */
    default void send(GenericFutureListener<ChannelFuture>... futures) {
        // outMsg co the lay buffer tu noi khac, sau khi duoc copy vai lan, nen van can phai release sau khi flushoutgoing.headers().setHeader("strategyDrop", String.valueOf(ConnectionManager.strategyDropGet()));
        //pre checked at constructor -> no need to check
        ReplyContext.sendByChannel(getChannel(), getCoMsg(), futures); // do not return bcase future resolved is immutable
    }

    /**
     * call by orphan flows, so need to decompose msg
     *
     * @param channel
     * @param msg
     * @param waitFlushTime
     * @param futures
     * @param <Res>
     * @return
     */
    MonitorManager mon = MonitorManager.getInstance();
    Logger logger = LogManager.getLogger(ReplyContext.class);

    /**
     * sendByChannel perform async flush -> message or ctx can be decompose before netty Encoder truly flush content -> need to call sync here then return a completed future
     *
     * @param channel
     * @param msg
     * @param futures
     * @return
     */
    static void sendByChannel(Channel channel, ChassisMgrableMsg msg, GenericFutureListener<ChannelFuture>... futures) {
//		if(System.getenv())
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                long start = TimeUtils.nowNano();
                int msgLength = msg.readableBytes();
                String messageId = msg.getMessageId();
                if (msg.addServerState(ServerChannelAttribute.getServer(channel)) && logger.isDebugEnabled())
                    logger.debug("msgId {} stopController reject connection", messageId);

                if (channel != null) {
                    ChannelPromise channelPromise = channel.newPromise();
                    if (futures != null) channelPromise.addListeners(futures);
                    channelPromise.addListener(f -> {
                        mon.serverFlush(start);
                        if (logger.isDebugEnabled())
                            logger.debug("Flushed message {} headers {} contain {} bytes on channel {}", messageId, msg.headers(), msgLength, channel);
                        ChannelAttribute.getCounter(channel).getState().stack();
                    });
                    if (logger.isDebugEnabled())
                        logger.debug("Flushing message {} headers {} contain {} bytes on channel {}", messageId, msg.headers(), msg.readableBytes(), channel);
                    channel.writeAndFlush(msg, channelPromise);
                } else if (logger.isDebugEnabled())
                    logger.info("Null channel cannot send from ReplyContext msg {}", messageId);
            }
        };
        if (!CGW_REPLY_NEW_THREAD) {
            runnable.run();
        } else {
            CompletableFuture.runAsync(runnable);
        }
    }
}
