package com.viettel.autotest.microchassis.benchmark.peer;

import com.viettel.autotest.microchassis.lib.depricatedNeedRefactor.AsyncClientHandler;
import com.viettel.autotest.microchassis.lib.depricatedNeedRefactor.BenchmarkClient;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.connection.config.SslConfiguration;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpMethod;
import org.jctools.queues.MpmcArrayQueue;

import java.util.concurrent.atomic.AtomicLong;

public class Http2BenchmarkClient implements BenchmarkClient {
	private HttpClient client;
	Handler<Object> callBackSend;
	public MpmcArrayQueue<HttpClientReceiveContext> queue = Boolean.getBoolean("poolResponse") ? new MpmcArrayQueue<>(1000) : null;
	public AtomicLong countCreate = new AtomicLong();

	@Override
	public Channel sendAsync(byte[] data, String requestID) throws Exception {
		return null;
	}
	@Override
	public void init(String host, int port, AsyncClientHandler handler) throws Exception {
		HttpClientOptions httpClientOptions = new HttpClientOptions(host, port, "http");
		if (Boolean.getBoolean("ssl")) {
			httpClientOptions.setSslConfiguration(new SslConfiguration()
				.setCertPath(System.getProperty("certPath", "../etc/cert_nodes.pem")));
		}
		client = httpClientOptions.newClientHttp2Only();
		client.start(new HttpClientHandler(httpClientOptions));
	}

	@Override
	public void send(byte[] data, String id) throws Exception {
		client.send(client.createReq(HttpMethod.GET, "test", data), id);
	}

	@Override
	public HttpResponse sendSync(byte[] data, String requestID) throws Exception {
		HttpRequest request = client.createReq(HttpMethod.GET, "test", data);
		return client.sendSync(request);
	}

	@Override
	public void setCallBack(Handler<Object> callBackSend) {
		this.callBackSend = callBackSend;
	}

}
