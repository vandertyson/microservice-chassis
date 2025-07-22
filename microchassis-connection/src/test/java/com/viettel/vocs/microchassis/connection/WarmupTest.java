package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class WarmupTest {
	protected static final Logger logger = LogManager.getLogger(WarmupTest.class);

	@BeforeAll
	public void setUpClass(){
		LogUtils.setupLog4j2();
	}

	@Test
	void warmup() {
		AtomicLong sent = new AtomicLong();
		AtomicLong warm = new AtomicLong();
		AtomicLong cold = new AtomicLong();
		AtomicBoolean useWarm = new AtomicBoolean(false);

		Executors.newSingleThreadExecutor().submit(() -> {
			long sleepMs = 1000;
			long start = System.currentTimeMillis();
			while (sleepMs >= 50) {
				TimeUtils.waitSafeMili(sleepMs);
				useWarm.set(true);
				if (warm.get() > 0 && (System.currentTimeMillis() - start > 10_000)) {
					sleepMs = sleepMs / 2;
					logger.info("sleep ms = {}, tps warm = {}", sleepMs, 1000 / sleepMs);
					start = System.currentTimeMillis();
				}
			}
			logger.info("====Finish warmup====");
			useWarm.set(false);
		});
		int numThread = 4;
		for (int i = 0; i < numThread; i++) {
			Executors.newSingleThreadExecutor().submit((Runnable) () -> {
				while (true) {
					try {
						TimeUtils.waitSafeMili(1);
						sent.incrementAndGet();
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
		long runSec = 60;
		long deltaCheck = 1000;
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < runSec * 1000) {
			TimeUtils.waitSafeMili(deltaCheck);
			long l = sent.get();
			double l1 = (l - sentCheck) * 1000f / deltaCheck;
			sentCheck = l;
			logger.info("Sent {}, cold {}, warm {}, tps={}", l, cold.get(), warm.get(), l1);
		}
		isRunning.set(false);
	}
}
