package com.viettel.autotest.microchassis.benchmark;

import io.netty.buffer.PooledByteBufAllocator;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class BenchmarkClientConfiguration {
	public enum ServiceType {
		echo,
		forward,
		generator
	}

	public static String clientMode = System.getProperty("clientMode", "async"); // db default is sync and echo
	public static String clientModeCode = clientMode.substring(0, Math.min(clientMode.length(), 2));
	@Getter
	@Setter
	public String protocol = System.getProperty("protocol", "tcp");
	@Getter
	@Setter
	int numThread = Integer.getInteger("numThread", 1);
	@Getter
	@Setter
	String host = System.getProperty("host", "localhost");
	@Getter
	@Setter
	int port = Integer.getInteger("port", 8088);
	@Getter
	@Setter
	boolean affinity = Boolean.getBoolean("affinity");
	@Getter
	@Setter
	boolean sharePool = Boolean.getBoolean("sharePool");
	@Getter
	@Setter
	Long totalReqeust = Long.getLong("totalRequest", 200000);
	@Getter
	@Setter
	int poolSize = Integer.getInteger("poolSize", 1);
	@Getter
	@Setter
	int payloadSize = Integer.getInteger("payloadSize", 4096);
	@Getter
	@Setter
	int concurent = Integer.getInteger("concurent", 1);
	@Getter
	@Setter
	boolean md5 = Boolean.getBoolean("md5");
	@Getter
	@Setter
	PooledByteBufAllocator alloc = new PooledByteBufAllocator(true);
	@Getter
	@Setter
	String taskSetCpu = System.getProperty("taskSetCpu");
	@Getter
	@Setter
	List<Integer> listCpu;
	@Getter
	@Setter
	boolean serviceDiscovery = Boolean.parseBoolean(System.getProperty("serviceDiscovery", "TRUEx"));
	@Getter
	@Setter
	int pingInterval = Integer.getInteger("pingInterval", 1000);
	@Getter
	@Setter
	boolean enableSSL = Boolean.getBoolean("enableSSL");
	@Getter
	@Setter
	long timeOutMs = Long.getLong("timeOutMs", 2000);
	@Getter
	@Setter
	Integer resetStreamThreshold = Integer.getInteger("resetStreamThreshold", 2_000_000);
	@Getter
	@Setter
	Integer maxWaitCompleteSec = Integer.getInteger("maxWaitCompleteSec", 300);
	@Getter
	@Setter
	final long metricCollectIntervalMs = Long.getLong("metricCollectIntervalMs", 5000);
	@Getter
	@Setter
	final String config = System.getProperty("config");
	@Getter
	@Setter
	Integer routeCheckInterval = Integer.getInteger("routeCheckInterval", 0);
	@Getter
	@Setter
	String rateConfigPath = System.getProperty("rateConfigPath", "");
	@Getter
	@Setter
	String circuitPath = System.getProperty("circuitPath", "");
	@Getter
	@Setter
	boolean enableMetrics = Boolean.getBoolean("enableInternalMetrics");

	public static void updateClientMode(String newClientMode) {
		clientMode = newClientMode;
		clientModeCode = clientMode.substring(0, Math.min(clientMode.length(), 2));
	}

	@Override
	public String toString() {
		return "BenchmarkClientConfiguration{}";
	}
}
