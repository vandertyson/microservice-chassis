/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.codec.io;

import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.IllegalReferenceCountException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class H1CIHandler extends HCIHandler {

    private static final Logger logger = LogManager.getLogger(H1CIHandler.class);

    public H1CIHandler(EventHandler eventHandler, HttpClientHandler handler, MsgCounter msgCounter) {
        super(handler, eventHandler, msgCounter);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        if (eventHandler != null)
            eventHandler.triggerEvent(new ContextEvent(ContextEvent.EventType.CHANNEL_ACTIVE, ctx));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (eventHandler != null)
            eventHandler.triggerEvent(new ContextEvent(ContextEvent.EventType.CHANNEL_INACTIVE, ctx));
    }
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        if (eventHandler != null)
            eventHandler.triggerEvent(new ContextEvent(ContextEvent.EventType.CHANNEL_INACTIVE, ctx));

    }
    @Override
    public void channelRead0(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpResponse msg)  {
        super.channelRead0(ctx, stack(new HttpResponse(HttpResponse.toFullRetain(msg))));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if(!(cause instanceof IllegalReferenceCountException)){
            super.exceptionCaught(ctx, cause);
            logger.error(cause, cause);
        }
    }
}
