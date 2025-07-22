package com.viettel.autotest.microchassis;

import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * @author tiennn18
 */
public class HttpVdu {
	protected static final Logger logger = LogManager.getLogger(HttpVdu.class);
	static HttpServer server;
	static HttpClient client;
	static int port = 8080;


	static void setupServer() {
		try {
			HttpServerOptions options = new HttpServerOptions("test", port);
			server = new HttpServer(options);
			server.start(makeHttpServerHandler.apply(options));
			logger.info("===================SERVER FOR TEST STARTED===================");
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	static Function<HttpClientOptions, HttpClientHandler> makeHttpClientHandler = config -> new HttpClientHandler(config) {
		public void handle(HttpClientReceiveContext ctx) {
			HttpResponse inMsg = ctx.getInMsg();
			logger.info("Receive async response |Status: " + inMsg.status() + "|head: " + inMsg.headers() + "|msg: " + inMsg.toString(StandardCharsets.UTF_8));
		}

		@Override
		public void dropHandle(HttpRequest failedReq, boolean isSent) {
			super.dropHandle(failedReq, isSent);
		}

		@Override
		public void timeoutHandle(String requestID) {
			super.timeoutHandle(requestID);
		}
	};


	static Function<HttpServerOptions, HttpServerHandler> makeHttpServerHandler = config -> new HttpServerHandler(config) {
		@Override
		public void handle(HttpServerContext ctx) {
			try {
				byte[] payload = ctx.getInData();
				logger.info("received msg th msgId " + ctx.getInID() + " data " + ctx.getInMsg().toStringUTF8());
				ctx.getCoMsg().headers().set(ctx.getInMsg().headers()); // echo headers
				if (payload.length > 0) {
					ctx.send(payload);
				} else {
					ctx.send("Hi");
				}
			} catch (Exception e) {
				logger.error(e, e);
			}
		}
	};

	public static void main(String[] args) {
		LogUtils.setupLog4j2();
		logger.info("Setting up");
		try {
			setupServer();
			TimeUtils.waitSafeMili(100);
			setUpClient(false);
		} catch (Exception e) {
			logger.error(e, e);
		}
		testSyncRequest();
		testAsyncRequest();

		try {
			logger.info("tearDownClass");
			client.close();
			server.stop();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	static void setUpClient(boolean http2) {
		HttpClientOptions config = new HttpClientOptions("localhost", port, "test");
		try {
			(client = http2 ? config.newClientHttp2Only() : config.newClientHttp1Only()).start(makeHttpClientHandler.apply(config));
			logger.info("===================CLIENT FOR TEST CONNECTED===================");
		} catch (Exception e) {
			logger.error(e, e);
		}
	}


	static HttpRequest makeRequest(HttpMethod method) {
		HttpRequest req = client.createReq(method, PayloadGenerator.testUrl);
		req.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP);
		String payload = "{\"requestId\":\""+req.getMessageId()+"\",\"msisdn\":\"6000001\",\"sessionType\":null,\"sessionName\":null,\"deviceId\":null,\"reqType\":\"mobile\",\"featureFlag\":null}";
		req.writeFrom(payload.getBytes(StandardCharsets.UTF_8));
		return req;
	}

	// SEND
	static void testAsyncRequest() {
		try {
			HttpRequest req = makeRequest(HttpMethod.POST);
			client.send(req);
		} catch (TimeoutException e) {
			logger.error("Send timeout", e);
		} catch (Exception e) {
			logger.error("Send failed", e);
		}
	}

	static void testSyncRequest() {
		try {
			HttpRequest req = makeRequest(HttpMethod.POST);
			HttpResponse sendSync = client.sendSync(req);
			if(sendSync!= null) sendSync.decompose();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
}
