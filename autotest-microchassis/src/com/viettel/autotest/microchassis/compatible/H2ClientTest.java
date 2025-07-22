/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.autotest.microchassis.compatible;

import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.config.SslConfiguration;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author vttek
 */
public class H2ClientTest {

	static final Logger logger = LogManager.getLogger(H2ClientTest.class);
	static String host = System.getProperty("host", "localhost");
	static int port = Integer.getInteger("port", 8088);
	static boolean ssl = Boolean.getBoolean("ssl");
	static String certPath = System.getProperty("certPath", "./etc/cert_nodes.pem");
	static byte[] payload = System.getProperty("payload", "Good day thuanlk!").getBytes();
	static long total = Long.getLong("totalSend", 1000);
	static long sleepPersend = Long.getLong("sleepPerSend", 100);
	static long per = Long.getLong("per", 20);
	static AtomicLong count = new AtomicLong();
	static AtomicLong latency = new AtomicLong();
	static AtomicBoolean finish = new AtomicBoolean();
	static Map<String, Long> mapTime = new ConcurrentHashMap<>();

	public static void main(String[] args) throws Exception {
		LogUtils.setupLog4j2();
		Scanner myObj = new Scanner(System.in);  // Create a Scanner object
		HttpClientOptions opts = new HttpClientOptions(host, port, "client");
		if (ssl) opts.setSslConfiguration(new SslConfiguration().setCertPath(certPath));
		HttpClient client = opts.newClientHttp2Only();
		client.start(new HttpClientHandler(opts) {
			@Override
			public void handle(HttpClientReceiveContext hctx) {
				long incrementAndGet = count.incrementAndGet();
				String get = hctx.getInMsg().getMessageId();
				Long remove = mapTime.remove(get);
				if (remove != null) {
					latency.addAndGet(System.currentTimeMillis() - remove);
				}
				if (incrementAndGet % per == 0) {
					logger.info(String.format("Received %d/%d. Avg latency: %d ms", incrementAndGet, total, latency.get() / per));
					latency.set(0);
				}
				if (incrementAndGet == total) {
					finish.set(true);
				}
			}
		});
		ConnectionManager.getInstance().startMonitorThread();
		try {
			while (true) {
				System.out.println("\n================================\nCOMMAND LIST");
				System.out.println("1. Send a request");
				System.out.println("2. Send many request");
				System.out.print("ENTER COMMAND NUMBER: ");
				String cmd = myObj.nextLine();  // Read user input
				Integer command;
				try {
					command = Integer.valueOf(cmd);
				} catch (Exception e) {
					continue;
				}
				if (command.equals(1)) {
					HttpRequest rq = client.createReq(HttpMethod.GET, "/hello", payload);
					try {
						HttpResponse response = client.sendSync(rq);
						printResponse(response);
					} catch (Exception e) {
						if (e instanceof TimeoutException) {
							System.out.println("Error. Request timed out");
						} else {
							e.printStackTrace();
						}
					}
					continue;
				}

				if (command.equals(2)) {
					try {
						System.out.print("Enter number of request: ");
						total = Integer.parseInt(myObj.nextLine());
						count.set(0);
						System.out.println("Start send");
						for (int i = 0; i < total; i++) {
							TimeUtils.waitSafeMili(sleepPersend);
							HttpRequest rq = client.createReq(HttpMethod.GET, "/hello", payload);
							client.send(rq, "rq" + i);
							mapTime.put("rq" + i, System.currentTimeMillis());
						}
						System.out.println("Sent " + total + " request. Waiting for responses");
						while (!finish.get()) {
							TimeUtils.waitSafeMili(1);
						}
						TimeUtils.waitSafeMili(500);
						System.out.println("Finish received " + total + " responses");
					} catch (Exception e) {
						e.printStackTrace();
					}
					continue;
				}
				System.out.println("Command not found");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void printResponse(HttpResponse response) {
		System.out.println("Receive response from server"
			+ "\n Status: " + response.status()
			+ "\n Headers: " + response.headers()
			+ "\n Payload: " + response.toString(Charset.defaultCharset()));
		response.decompose();
	}
}
