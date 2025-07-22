/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.autotest.microchassis.lib.depricatedNeedRefactor;

import com.viettel.autotest.microchassis.benchmark.peer.Http2BenchmarkClient;
import com.viettel.autotest.microchassis.compatible.TcpBenchmarkClient;
import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.autotest.microchassis.benchmark.peer.ClientSendSync;
import com.viettel.autotest.microchassis.benchmark.peer.ClientSendThread;
import com.viettel.autotest.microchassis.benchmark.peer.SyncThread;
import com.viettel.autotest.microchassis.benchmark.BenchmarkClientConfiguration;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.viettel.vocs.microchassis.base.ChassisConst.SupportVersion.HTTP;

/**
 * @author vttek
 */
public class Client {

	private static final Logger logger = LogManager.getLogger(Client.class);
	static ConcurrentHashMap<String, Long> mapSend = new ConcurrentHashMap<>();
	static AtomicLong reqID = new AtomicLong();
	static AtomicLong fail = new AtomicLong(0);
	static AtomicLong totalSend = new AtomicLong(0);
	static AtomicLong totalReceive = new AtomicLong(0);
	static final Map<String, AtomicLong> channelSendCount = new ConcurrentHashMap<>();
	static final Map<String, AtomicLong> channelReceiveCount = new ConcurrentHashMap<>();

	public static void main(String[] args) throws Exception {
		LogUtils.setupLog4j2();
		BenchmarkClientConfiguration config = new BenchmarkClientConfiguration();
		logger.info(ReflectionToStringBuilder.toString(config, ToStringStyle.MULTI_LINE_STYLE, false));

		BenchmarkClient client = null;
		AsyncClientHandler handler = new AsyncClientHandler(totalReceive, config.getTotalReqeust(), fail, channelReceiveCount, config.getHost() + ":" + config.getPort());
		if (config.isSharePool()) {
			client = createClient(config.protocol);
			client.init(config.getHost(), config.getPort(), handler);
		}
		ExecutorService service = Executors.newFixedThreadPool(
			config.isAffinity()
				? config.getNumThread() + 1
				: config.getNumThread(),
			config.isAffinity()
				? new AffinityThreadFactory("atf_client_" + BenchmarkClientConfiguration.clientMode, AffinityStrategies.DIFFERENT_CORE)
				: Executors.defaultThreadFactory());
		for (int i = 0; i < config.getNumThread(); i++) {
			BenchmarkClient myClient;
			if (config.isSharePool()) {
				myClient = client;
			} else {
				myClient = createClient(config.protocol);
				myClient.init(config.getHost(), config.getPort(), handler);
			}
			if (Boolean.parseBoolean(System.getProperty("sync", "TRUE"))) {
				if (Boolean.getBoolean("trueSync")) {
					SyncThread t = new SyncThread(
						config.getHost(),
						config.getPort(),
						config.getPayloadSize(),
						config.getConcurent(),
						PayloadGenerator.testUrl,
						totalSend,
						totalReceive,
						config.getTotalReqeust(),
						fail,
						myClient,
						channelSendCount,
						channelReceiveCount);
					service.execute(t);
				} else {
					ClientSendSync t = new ClientSendSync(config,
						PayloadGenerator.testUrl,
						totalSend,
						totalReceive,
						fail,
						myClient,
						channelSendCount,
						channelReceiveCount);
					service.execute(t);
				}
			} else {
				ClientSendThread t = new ClientSendThread(
					config.getHost(),
					config.getPort(),
					config.getPayloadSize(),
					config.getConcurent(),
					PayloadGenerator.testUrl,
					totalSend,
					totalReceive,
					config.getTotalReqeust(),
					fail,
					myClient,
					channelSendCount,
					channelReceiveCount);
				service.execute(t);
			}
			ConnectionManager.getInstance().startMonitorThread();
			service.awaitTermination(100, TimeUnit.DAYS);
		}
	}

	public static BenchmarkClient createClient(String protocol) {
			if (protocol.equals("tcp")) {
					return new TcpBenchmarkClient();
			}
			if (protocol.equals(HTTP)) {
					return new Http2BenchmarkClient();
			}
			if (protocol.equals("old")) {
					return new OldTcpClient();
			}
			return null;
	}
}
