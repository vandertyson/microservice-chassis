/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.client;

import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.connection.client.ChannelMonitor;
import com.viettel.vocs.microchassis.connection.client.ClientChannelAttribute;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import com.viettel.vocs.microchassis.connection.dns.HostNameResolver;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.ssl.SslContext;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author vttek
 */
public class HttpClient extends NettyClient<HttpRequest, HttpResponse, HttpClientOptions, HttpClientHandler> implements AutoCloseable{
	private final MsgCounter msgCounter;
	protected final HttpScheme scheme;
	protected final SslContext sslCtx;
	@Override
	public HttpClientOptions getConfig() {
		return super.getConfig();
	}
	private final HealthCheckMonitor healthCheckMonitor = HealthCheckMonitor.instance;

	public HttpClient(HttpClientOptions config) throws Exception {
		super(config);
		msgCounter = MsgCounter.ofClient(config.id, null);
		Objects.requireNonNull(config.http2, "http2 configuration is required");
		Objects.requireNonNull(config.http1, "http1 configuration is required");
		sslCtx = config.sslConfiguration != null ? config.sslConfiguration.build(false) : null;
		scheme = config.scheme();
		try {
			healthCheckMonitor.register(this, HostNameResolver.doResolveDNS(config.host).stream().map(ip -> Endpoint.newEndpoint(ip, config.port)).toArray(Endpoint[]::new));
		} catch (Exception ex) {
			healthCheckMonitor.register(this, Endpoint.newEndpoint(config.host, config.port));
		}
		eventHandler.addEventHandler(ContextEvent.EventType.CHANNEL_INACTIVE, (Consumer<ContextEvent>) event -> {
			HttpClientConnection conn = ClientChannelAttribute.getConnection(event.getChannel());
			if(conn != null/* && conn.isEnable()*/) conn.deregister(); // an active conn need to close -> conn.close call channel close -> trigger CHANNEL_INACTIVE trigger this event handler -> call loop close conn again -> break by check enabling
		});
	}

	@Override
	public void refresh() {
		super.refresh();
		revokeDisconnected(routable -> ((HttpClientConnection)routable).isHttp1()); // only revoke http1 connection, http2 will be handled like TCP
	}

	@Override
	public HttpClientConnection createConnection(Site<HttpClientOptions> ownerSite, Endpoint endpoint, EventHandler eventHandler, boolean monitor) {
		return createConnection(ownerSite, endpoint, handler, eventHandler, false);
	}

	@Override
	public final HttpClientConnection createConnection(Site<HttpClientOptions> ownerSite, Endpoint endpoint, HttpClientHandler handler, EventHandler eventHandler, boolean monitor) {
		HttpClientConnection conn = new HttpClientConnection(sslCtx, config, ownerSite, eventHandler, endpoint, handler, bootstrap, ownerSite.getRouteStrategyRef());
		if(!conn.isClosed()){  // not to be closed after register
			if (monitor) ChannelMonitor.getInstance().registerMonitorObject(conn);
//			if (config.monitorIntervalMs > 0 && monitor) // http1 not register for monitor due to single packet send natural
//				ChannelMonitor.getInstance().registerMonitorObject(conn);
////			logger.info(conn.isConnected()
////					? "Created connection to {} (channelActive={} connectedBeforeAdd={}/conPerIP={})"
////					: "Connection to {} error, queued for monitor retry (channelActive={} connected={} conPerIP={})",
////				endpoint, conn.isConnected(), countConnected(endpoint), config.connectionPerIP);// server drop make channel die /* khong bi server deny */
			return conn;
		} else return null;
	}
	@Override
	public void close() {
		super.close();
		if (config.healthCheck != null) healthCheckMonitor.unregister(this);
	}


	public HttpRequest createReq(HttpMethod method, String path) {
		return new HttpRequest(bytebufAllocator, method, path);
	}


	public HttpRequest createReq(HttpMethod method, String path, byte[] data) {
		return (HttpRequest) createReq(method, path).writeFrom(data);
	}
	public HttpRequest createReq(int outInitSize, HttpMethod method, String path) {
		return new HttpRequest(bytebufAllocator, outInitSize, method, path);
	}
	public HttpRequest createReq(int outInitSize, HttpMethod method, String path, byte[] data) {
		return (HttpRequest) createReq(outInitSize, method, path).writeFrom(data);
	}
	public void send(HttpMethod method, String path, byte[] data) throws Exception {
		// create init request then send from byte
		send(createReq(method, path, data));
	}
	public void send(HttpMethod method, String path) throws Exception {
		// create init request then send from byte
		send(createReq(method, path));
	}
	public boolean isSendSyncable(){
		RouteStrategy routeStrategy = routeStrategyRef.get();
		return routeStrategy == null ? false : routeStrategy.getDestMap().values().stream().anyMatch(conn -> ((HttpClientConnection)conn).isSendSyncable());
	}

	public MsgCounter getMsgCounter() {
		return msgCounter;
	}
}
