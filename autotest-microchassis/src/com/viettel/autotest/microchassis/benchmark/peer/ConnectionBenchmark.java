package com.viettel.autotest.microchassis.benchmark.peer;


import com.viettel.vocs.common.log.LogUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ConnectionBenchmark {
	protected static final Logger logger = LogManager.getLogger(ConnectionBenchmark.class);
	public static final int numberOfRequest = 1000;
	public ExecutorService threadPool = Executors.newFixedThreadPool(2);
	public static final int port = 9088;
	public static final long timeout = 2000;
	protected static final AtomicLong id = new AtomicLong(0);
	protected final AtomicLong countSyncReceive = new AtomicLong(0);

	protected final AtomicLong countAsyncReceive = new AtomicLong(0);
	protected final AtomicLong serverRcv = new AtomicLong(0);
	protected final AtomicLong countSyncSend = new AtomicLong(0);
	protected final AtomicLong countAsyncSend = new AtomicLong(0);
	@BeforeAll
	public void setUpClass(){
		LogUtils.setupLog4j2();
	}

}
