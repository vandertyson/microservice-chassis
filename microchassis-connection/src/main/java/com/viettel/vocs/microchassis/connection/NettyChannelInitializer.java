package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.client.ChannelAttribute;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * base initializer for both client and server
 * initializer provide components for channel included:
 *  - ratelimiter
 *  - counter
 *      + BackState/State
 *  - msgDecoder/msgEncoder
 * holding:
 *  - eventHandler
 *  - connectionMode
 *  - routeStrategy
 */
public abstract class NettyChannelInitializer extends ChannelInitializer<SocketChannel>{

    protected final EventHandler eventHandler;
    private final List<InetAddress> whiteList;
    protected final MsgCounter msgCounter;
    protected NettyChannelInitializer(EventHandler handler, MsgCounter msgCounter, InetAddress... whiteList) {
        this.eventHandler = handler;
        this.whiteList = List.of(whiteList);
        this.msgCounter = msgCounter;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        if (!authen(ch)) ch.close();
        Endpoint e = Endpoint.remote(ch);
        if (e != null) ch.attr(ChannelAttribute.connectionEndpoint).set(e); // for LBR listener usages
    }
    private boolean authen(SocketChannel channel) {
        InetSocketAddress name = channel.remoteAddress();
        return whiteList == null || whiteList.isEmpty()
          || whiteList.stream().anyMatch(inetAddress -> inetAddress.equals(name.getAddress()));
    }
}
