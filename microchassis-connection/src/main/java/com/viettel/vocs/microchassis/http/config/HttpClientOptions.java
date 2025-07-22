/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.config;

import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.connection.config.*;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.RCP;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.RR;
import com.viettel.vocs.microchassis.http.client.HttpClient;

import java.util.Objects;

import static com.viettel.vocs.microchassis.base.ChassisConst.SupportVersion.HTTP;


/**
 * @author vttek
 */
public class HttpClientOptions extends ClientConfiguration implements HttpOptions {

	public HealthCheckConfiguration healthCheck;
	/**
	 * initProto=h1 negotiable=false => h1 only
	 * initProto=h1 negotiable=true => h1 upgradable (h1+h2) (default)
	 * initProto=h2 negotiable=true => h2 fallback-able h1 (h1+h2)
	 * initProto=h2 negotiable=false => h2 only (no fallback-able h1)
	 */
	public SslConfiguration sslConfiguration = null; // both H1 & H2
	public String initProto = "HTTP/1.1";
	public boolean negotiable = false;
	public Http2ChannelConfigure http2 = new Http2ChannelConfigure();
	public Http1ChannelConfigure http1 = new Http1ChannelConfigure();

	//	protected ConcurrentHashMap<String, HttpVersionConfigures> protoConfigs = new ConcurrentHashMap<>();
	public int maxHttpContentLength() {
		return Math.max(http1.maxContentLength, http2.maxContentLength);
	}

	@Override
	public boolean diff(PeerConfig obj) {
		if (super.diff(obj)) return true;
		if (!(obj instanceof HttpClientOptions)) return true;
		HttpClientOptions o = (HttpClientOptions) obj;
		return !Objects.equals(healthCheck, o.healthCheck)
//			|| !Objects.equals(protoConfigs, o.protoConfigs)
			|| !Objects.equals(sslConfiguration, o.sslConfiguration)
			|| !Objects.equals(http1, o.http1)
			|| !Objects.equals(http2, o.http2)
			;
	}

	public HttpClientOptions configHttp2Fallback() {
		negotiable = true;
		initProto = ChassisConst.SupportVersion.HTTP2_0;
		http1.keepAlive = true;
		routeStrategy = new RCP();
		return this;
	}
	public HttpClientOptions configHttp2Only() {
		negotiable = false;
		initProto = ChassisConst.SupportVersion.HTTP2_0;
		routeStrategy = new RR();
		return this;
	}
	public HttpClientOptions configHttp1Upgrade() {
		negotiable = true;
		initProto = ChassisConst.SupportVersion.HTTP1_1;
		routeStrategy = new RCP();
		return this;
	}
	public HttpClientOptions configHttp1Only() {
		negotiable = false;
		initProto = ChassisConst.SupportVersion.HTTP1_1;
		routeStrategy = new RCP();
		return this;
	}
	public HttpClient newClientHttp2Only() throws Exception {
		return new HttpClient(configHttp2Only());
	}

	public HttpClient newClientHttp2Fallback() throws Exception {
		return new HttpClient(configHttp2Fallback());
	}

	public HttpClient newClientHttp1Upgrade() throws Exception {
		return new HttpClient(configHttp1Upgrade());
	}

	public HttpClient newClientHttp1Only() throws Exception {
		return new HttpClient(configHttp1Only());
	}

	public static HttpClientOptions createInstanceConf(String clientID) {
		return (HttpClientOptions) new HttpClientOptions("localhost", 9000, clientID)
			.setChannelConfiguration(new ChannelConfiguration().setAffinity(false))
			.setSendTimeoutMs(ChassisConfig.ConnectionConfig.Http1Config.HTTP1_TIMEOUT_MS.get())
			.setPingIntervalMs(0);
	}

	public HttpClientOptions(String host, int port, String clientID) {
		super(host, port, clientID, ChassisConst.STANDALONE_LBR_ID, 0, 1, 1, "fullmesh", new RCP()); // 0 mean no limits
	}

	public HttpClientOptions(String clientId) {
		this("localhost", 9000, clientId);
	}
	protected HttpClientOptions() {
		super();
	}
	@Override
	public HttpClientOptions setSslConfiguration(SslConfiguration sslConfiguration) {
		this.sslConfiguration = sslConfiguration;
		return this;
	}


	@Override
	public boolean isSsl() {
		return sslConfiguration != null;
	}

//	@Override
//	public Set<HttpVersionConfigures> getSupportConfigs() {
//		return new HashSet<>(getProtoConfigs().values());
//	}


	public HttpClientOptions setHealthCheck(HealthCheckConfiguration healthCheck) {
		this.healthCheck = healthCheck;
		return this;
	}

	public String getProtocolShortName() {
		return ChassisConst.SupportVersion.HTTP_SHORT; // isSupport(ChassisConst.SupportVersion.HTTP_2_0) ? ChassisConst.SupportVersion.HTTP2_SHORT : ChassisConst.SupportVersion.HTTP1_SHORT;
	}

	@Override
	public String getProtocolName() {
		return HTTP;
	}
}
