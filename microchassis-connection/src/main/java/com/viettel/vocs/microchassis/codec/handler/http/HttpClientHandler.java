package com.viettel.vocs.microchassis.codec.handler.http;

import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.handler.ClientHandler;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;

public class HttpClientHandler extends ClientHandler<HttpRequest, HttpResponse, HttpClientReceiveContext>{
	public HttpClientHandler(HttpClientOptions config) {
		super(config);
	}
}

