/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.config;

import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.config.SslConfiguration;
import com.viettel.vocs.microchassis.http.server.HttpServer;

import java.util.Objects;

/**
 * @author vttek
 */
public class HttpServerOptions extends ServerConfiguration implements HttpOptions {
	public SslConfiguration sslConfiguration = null; // both H1 & H2
	public Http2ChannelConfigure http2 = new Http2ChannelConfigure();
	public Http1ChannelConfigure http1 = new Http1ChannelConfigure();

	public int maxHttpContentLength(){
		return Math.max(http1.maxContentLength, http2.maxContentLength);
	}
	@Override
	public boolean diff(PeerConfig obj) {
		if (super.diff(obj)) return true;
		if (!(obj instanceof HttpServerOptions)) return true;
		HttpServerOptions o = (HttpServerOptions) obj;
		return !Objects.equals(sslConfiguration, o.sslConfiguration)
			|| !Objects.equals(http1, o.http1)
			|| !Objects.equals(http2, o.http2)
			;
	}
	public HttpServer newServer() throws Exception {
		return new HttpServer(this); // anySupport(ChassisConst.SupportVersion.HTTP_2_0, ChassisConst.SupportVersion.HTTP_1_1) ?  : null;
	}
	public HttpServerOptions(String id, int port) {
		super(id, port);
	}

	public HttpServerOptions() {
		super();
	}
	public HttpServerOptions(String serverId) {
		super(serverId);
	}

	@Override
	public HttpServerOptions setSslConfiguration(SslConfiguration sslConfiguration) {
		this.sslConfiguration = sslConfiguration;
		return this;
	}



	@Override
	public boolean isSsl() {
		return sslConfiguration != null;
	}

	@Override
	public String getProtocolName() {
		return scheme().toString();
	}

	public String getProtocolShortName() {
		return ChassisConst.SupportVersion.HTTP_SHORT;//  isSupport(ChassisConst.SupportVersion.HTTP_2_0) ? ChassisConst.SupportVersion.HTTP2_SHORT : ChassisConst.SupportVersion.HTTP1_SHORT;
	}
}
