package com.viettel.autotest.microchassis.compatible;


import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Warmup {
	static final Logger logger = LogManager.getLogger(HServerTest.class);

	public static void main(String[] args) {
		LogUtils.setupLog4j2();
		AtomicLong sent = new AtomicLong();
		AtomicLong warm = new AtomicLong();
		AtomicLong cold = new AtomicLong();
		AtomicBoolean useWarm = new AtomicBoolean(false);
		Thread t = new Thread(() -> {
			long sleepMs = 1000;
			long start = System.currentTimeMillis();
			while (sleepMs >= 1) {
				TimeUtils.waitSafeMili(sleepMs);
				useWarm.set(true);
				if (warm.get() > 0 && (System.currentTimeMillis() - start > 10_000)) {
					sleepMs = sleepMs / 2;
					logger.info("sleep ms = {}, tps warm = {}", sleepMs, 1000 / Math.max(sleepMs, 1));
					start = System.currentTimeMillis();
				}
			}
			logger.info("====Finish warmup====");
			useWarm.set(false);
		});
		t.setName("warm");
		t.start();
		int numThread = 3;
		for (int i = 0; i < numThread; i++) {
			Executors.newSingleThreadExecutor().submit((Runnable) () -> {
				while (true) {
					try {
						sent.incrementAndGet();
						TimeUtils.waitSafeMili(1);
						if (useWarm.getAndSet(false)) {
							warm.incrementAndGet();
						} else {
							cold.incrementAndGet();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
		long sentCheck = 0;
		AtomicBoolean isRunning = new AtomicBoolean(true);
		long runSec = 180;
		long deltaCheck = 5000;
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < runSec * 1000) {
			TimeUtils.waitSafeMili(deltaCheck);
			long l = sent.get();
			double l1 = (l - sentCheck) * 1000f / deltaCheck;
			sentCheck = l;
			logger.info("Sent {}, cold {}, warm {}, tps={}, alive={}", l, cold.get(), warm.get(), l1, t.isAlive());
		}
		isRunning.set(false);
		logger.info(t.isAlive());
	}
}
