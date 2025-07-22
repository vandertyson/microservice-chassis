package com.viettel.vocs.microchassis.http.client;

import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.client.InstanceClient;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.ssl.SslContext;

public class HttpInstanceClient extends InstanceClient<HttpRequest, HttpResponse, HttpClientOptions, HttpClientHandler> {
	protected final HttpScheme scheme;
	protected final SslContext sslCtx;
	private final MsgCounter msgCounter;


	@Override
	public HttpClientConnection createConnection(Site<HttpClientOptions> ownerSite, Endpoint endpoint, EventHandler eventHandler, boolean monitor) {
		return createConnection(ownerSite, endpoint, handler, eventHandler, monitor);
	}

	@Override
	public final HttpClientConnection createConnection(Site<HttpClientOptions> ownerSite, Endpoint endpoint, HttpClientHandler handler, EventHandler eventHandler, boolean monitor) {
		HttpClientConnection conn = new HttpClientConnection(sslCtx, config, ownerSite, eventHandler, endpoint, handler, bootstrap, ownerSite.getRouteStrategyRef()); // init included
		return !conn.isClosed() ? conn : null;// not to be closed after register
	}

	public HttpRequest createReq(HttpMethod method, String path) {
		return new HttpRequest(bytebufAllocator, method, path);
	}
	public HttpRequest createReq(HttpMethod method, String path, byte[] data) {
		return (HttpRequest) new HttpRequest(bytebufAllocator, method, path).writeFrom(data);
	}
	public HttpInstanceClient(HttpClientOptions config, HttpClientHandler handler) throws Exception {
		super(config, handler);
		msgCounter = MsgCounter.ofClient(config.id, null);
		scheme = config.scheme();
		sslCtx = config.sslConfiguration != null ? config.sslConfiguration.build(false) : null;
	}
	public HttpInstanceClient(HttpClientOptions instance1Conf) throws Exception {
		this(instance1Conf,
//			ClientHandlers.newInstance().setProtoVersionHandler(ChassisConst.SupportVersion.HTTP1_1,
					new HttpClientHandler(instance1Conf)
//				)
		); // config chi tao 1 lan at specific class -> set to handler
	}
	public static HttpInstanceClient createInstance(String clientId) throws Exception {
		return new HttpInstanceClient(HttpClientOptions.createInstanceConf(clientId));
	}

	public MsgCounter getMsgCounter() {
		return msgCounter;
	}
}
