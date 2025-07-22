package com.viettel.autotest.microchassis.feature;

import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;

class ClientClose {
	private static final Logger logger = LogManager.getLogger(ClientClose.class);

	public static void main(String[] args) throws Exception {
		LogUtils.setupLog4j2();

		HttpClient client = new HttpClientOptions("172.20.3.59", 1111, "x").newClientHttp1Upgrade();

		Queue<Integer> queue = new ArrayBlockingQueue<>(1000);
		Executors.newSingleThreadExecutor().execute(() -> {
			while (TimeUtils.waitSafeMili(1)) queue.offer(1);
		});

		Executors.newSingleThreadExecutor().execute(() -> {
			while (true) {
				try {
					Integer poll = queue.poll();
					if (poll != null && (!client.isConnected())) {
						client.start(new HttpClientHandler(client.getConfig()) {
							@Override
							public void handle(HttpClientReceiveContext ctxHolder) {
								logger.info(ctxHolder.getInMsg().headers());
							}
						});
					}
				} catch (Exception ex) {
					logger.error(ex, ex);
				}
			}
		});

		Executors.newSingleThreadExecutor().execute(() -> {
			while (TimeUtils.waitSafeMili(1000)) if (!client.isConnected()) client.close();
		});
	}
}
