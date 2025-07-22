/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.tcp.client;

import com.viettel.vocs.microchassis.codec.handler.tcp.TcpBasedHandler;
import com.viettel.vocs.microchassis.connection.client.ChannelAttribute;
import com.viettel.vocs.microchassis.connection.client.ClientChannelAttribute;
import com.viettel.vocs.microchassis.connection.client.ClientChannelInitializer;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import com.viettel.vocs.microchassis.tcp.codec.TCIDecoder;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tcp.codec.MsgEncoder;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author vtcore_hungtv7
 * @author tiennn18: turn to base client for any kind of client transmit custom Req/Res based on Msg over TCP connection, can have custom <Conf>
 * user must define encode/decode of Req/Res into Msg.content,
 * header is handled by Msg
 * localHeaders are not transmited
 */
public abstract class TcpBasedClientChannelInitializer<BiDMsg extends Msg, Conf extends ClientConfiguration, CHandler extends TcpBasedHandler>
	extends ClientChannelInitializer<Conf, CHandler> {
	private static final Logger logger = LogManager.getLogger(TcpBasedClientChannelInitializer.class);

	protected TcpBasedClientChannelInitializer(TcpBasedClientConnection<BiDMsg,Conf,CHandler> conn, MsgCounter msgCounter) {
		super(conn, msgCounter);
	}

	/**
	 * must have this into msgDecoder constructor -> let mapSendTime final -> null safe
	 */
	protected abstract TCIDecoder<BiDMsg, CHandler> newClientMsgDecoder(EventHandler EventHandler, CHandler userHandler, Map<String, CompletableFuture> syncPromiseMap, ClientConfiguration config, AtomicReference<PeerCounter> counterRef, AtomicReference<ConnectionMode> connModeRef, MsgCounter msgCounter);



	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		super.initChannel(ch);
		ch.attr(TcpClientChannelAttribute.promiseMap).set(new ConcurrentHashMap<>());
		try {
			Map<String, CompletableFuture> syncPromiseMap = TcpClientChannelAttribute.getPromiseMap(ch);
			TCIDecoder<BiDMsg, CHandler> msgDecoder = newClientMsgDecoder(
				eventHandler, handler, syncPromiseMap, config,
				ChannelAttribute.getCounterRef(ch), ClientChannelAttribute.getConnModeRef(ch), msgCounter);
			ch.attr(TcpClientChannelAttribute.msgDecoder).set(msgDecoder);
			ChannelPipeline pipeline = ch.pipeline();
			pipeline
				.addLast(new MsgEncoder(this.config.channelConfiguration, this.config.id))
				.addLast(msgDecoder);
			if(logger.isDebugEnabled()){
				logger.debug("AFTER ADDLAST TCPc {}", pipeline);
				logger.info("NEW channel {} use new msgDecoder {} with initer {} map {}", ch, System.identityHashCode(msgDecoder), System.identityHashCode(this), System.identityHashCode(syncPromiseMap));
			}
		} catch (Exception e) {
			logger.info("Client init channel error");
			logger.error(e, e);
		}
	}

}
