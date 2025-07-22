/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.common.file.YamlUtils;
import com.viettel.vocs.common.log.slideMonitor.CountSlideMonitor;
import com.viettel.vocs.common.os.TasksetUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.EchoTcpHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.client.ChannelMonitor;
import com.viettel.vocs.microchassis.connection.client.InstanceClient;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.ConnectionConfiguration;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.dns.ServiceMonitor;
import com.viettel.vocs.microchassis.connection.exception.ClientException;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.CenterLBR;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.LBRAdapter;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.StandaloneLBR;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.MainSite;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ServerCounter;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.client.HttpInstanceClient;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import com.viettel.vocs.microchassis.metrics.Metric;
import com.viettel.vocs.microchassis.metrics.MetricCollector;
import com.viettel.vocs.microchassis.metrics.SimpleMetricEventHandler;
import com.viettel.vocs.microchassis.serviceRegistry.client.LBRClientConfiguration;
import com.viettel.vocs.microchassis.serviceRegistry.codec.handler.LbrHandler;
import com.viettel.vocs.microchassis.tcp.client.TcpClient;
import com.viettel.vocs.microchassis.tcp.server.TcpServer;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.internal.PlatformDependent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author vttek
 */
public class ConnectionManager {

	public static final AtomicBoolean tcpWarmedUp = new AtomicBoolean(false);
	private static final Logger logger = LogManager.getLogger(ConnectionManager.class);
	private static final Object lock = new Object();

	private static volatile HttpInstanceClient defaultHttpInstance;

	public static HttpInstanceClient getDefaultHttpInstance() throws ClientException {
		if (defaultHttpInstance == null)
			throw new ClientException("Required call ConnectionManger.getInstance().loadYmlConfig() before use getDefaultHttpInstance()");
		return defaultHttpInstance;
	}

	private static volatile ConnectionManager instance;
	private ConnectionConfiguration configuration = null;

	private static final Map<String, LBRAdapter> mapLbrClient = new ConcurrentHashMap<>(Map.of(ChassisConst.STANDALONE_LBR_ID, new StandaloneLBR(new LBRClientConfiguration()))); // guarantee at least 1 lbr standalone

	static {
		logger.error("{}support directBuffer API", PlatformDependent.directBufferPreferred() ? "Not " : "");
		logger.info("Pod ID:{}/IP:{}", CommonConfig.InstanceInfo.INSTANCE_ID, CommonConfig.InstanceInfo.HOST_IP);
		ThreadManager.JobMonitor.scheduleJob(new ServiceDiscoveryJob());
		RequestTimeoutMonitor.threadRequestTimeout.start();
		MetricCollector.registerApp().addEventHandler(new SimpleMetricEventHandler() {
			@Override
			public void onBeforeCache(Metric metric) {
				MsgCounter.counterMap.values().forEach(counter -> counter.addMetrics(metric));
			}
		});
	}

	public static String getMyDNS() {
		return instance != null && instance.getConfiguration() != null && !instance.getConfiguration().dnsName.isEmpty() ? instance.getConfiguration().dnsName : CommonConfig.InstanceInfo.AUTHEN_SERVICE_NAME.check();
	}

	public static final Map<String, MainSite> mapClient = new ConcurrentHashMap<>(); // all instance and normal client constructor added

	public static Stream<InstanceClient> streamInstancelients(boolean parallel) {
		return (parallel ? mapClient.values().parallelStream() : mapClient.values().stream())
			.filter(c -> c instanceof InstanceClient).map(c -> (InstanceClient) c);
	}

	public static Stream<NettyClient> streamNettyClients(boolean parallel) {
		return (parallel ? mapClient.values().parallelStream() : mapClient.values().stream())
			.filter(c -> c instanceof NettyClient).map(c -> (NettyClient) c);
	}

	public static final Map<String, NettyServer> mapServer = new ConcurrentHashMap<>();


	private final AtomicBoolean monitorStarted = new AtomicBoolean(false);
	private static final CountSlideMonitor dropSlicer = new CountSlideMonitor();

	public static int strategyDropGet() {
		return dropSlicer.get();
	}

	public static void strategyDropStack() {
		dropSlicer.stack();
	}

	public static void warmUpTcp(int msgSize, long numberWarmup) throws Exception {
		if (tcpWarmedUp.compareAndSet(false, true)) {
			logger.info("Start warmup TCP client/server");
			long start = TimeUtils.nowNano();
			ServerConfiguration warmServerConf = new ServerConfiguration("warmup", ChassisConst.WARMUP_PORT);
			TcpServer warmUpServer = new TcpServer(warmServerConf);
			warmUpServer.startWarmup(new EchoTcpHandler(warmServerConf));
			ClientConfiguration warmupClientConf = new ClientConfiguration(
				ChassisConst.WARMUP_HOST, ChassisConst.WARMUP_PORT, "warmup");
			warmupClientConf.channelConfiguration.setTimeoutConnectMs(10000);
			TcpClient warmUpClient = new TcpClient(warmupClientConf);
			AtomicInteger testCounter = new AtomicInteger(0);
			try {
				warmUpClient.start(new TcpHandler(warmupClientConf) {
					@Override
					public void handle(TcpContext ctx) {
						testCounter.incrementAndGet();
					}
				});
			} catch (TimeoutException e) {
				logger.error(e);
				throw new RuntimeException(e);
			}
			logger.info("Start send warmup Msg");
			for (int i = 0; i < numberWarmup; i++) {
				try {
					warmUpClient.send(new byte[msgSize], "/tiennn18");
//				System.out.println("Sent warmup "+ testCounter.get() +"/"+ numberWarmup);
				} catch (Exception ignored) {
				}
			}
			TimeUtils.completeUnder(() -> testCounter.get() == numberWarmup, ChassisConfig.ConnectionConfig.DEFAULT_SEND_TIMEOUT_MS.get());
			logger.info(String.format("%s after {}ms", testCounter.get() > 0
				? (String.format("%s%d TCP server-client messages", testCounter.get() == numberWarmup
				? "Full warmed up " : String.format("Partly warmed up %d/", testCounter.get()), numberWarmup))
				: "Warmed up TCP server/client completely failed"), TimeUtils.miliPassed(start));
			warmUpClient.close();
			warmUpServer.stop();
		}
	}

//	public static HttpInstanceClient getDefaultHttpInstance() {
//		HttpInstanceClient r = defaultHttpInstance;
//		if (r == null) {
//			synchronized (lockH) {
//				r = defaultHttpInstance;
//				if (r == null) {
//					try {
//						r = HttpInstanceClient.createInstance(ChassisConst.defaultHttpClientId);
//					} catch (Exception ignored) {
//					}
//					defaultHttpInstance = r;
//				}
//			}
//		}
//		return r;
//	}

	// HttpVersion HTTP_1_0 = HttpVersion.HTTP_1_0; // not specified
	public static String checkAsyncHandlerSupportVersion(Handler handler) {
		return (handler instanceof HttpClientHandler
			|| handler instanceof HttpServerHandler)
			? ChassisConst.SupportVersion.HTTP
			: handler instanceof TcpHandler
			? ChassisConst.SupportVersion.TCP
			: handler instanceof LbrHandler
			? ChassisConst.SupportVersion.LBR
			: null;
	}

	public static ConnectionManager getInstance() {
		ConnectionManager r = instance;
		if (r == null) {
			synchronized (lock) {
				r = instance;
				if (r == null) {
					r = new ConnectionManager();
					instance = r;
				}
			}
		}
		return r;
	}

	public ConnectionConfiguration getConfiguration() {
		return configuration;
	}

	private ConnectionConfiguration baseConfiguration = null;

	private void readConfigYml() throws IOException {
		try {
			logger.info("Read ConnectionManager at base config yml {}", new File(ChassisConfig.ConnectionConfig.DEFAULT_BASE_CONNECTION_PATH.get()).getAbsolutePath());
			baseConfiguration = YamlUtils.objectFromYaml(ConnectionConfiguration.class, ChassisConfig.ConnectionConfig.DEFAULT_BASE_CONNECTION_PATH.get());
			logger.info("[Parsing base connection configuration]\n" + YamlUtils.objectToPrettyYaml(baseConfiguration));
		} catch (Throwable e) {
			logger.error("Load base connection configuration failed", e);
		}
		try {
			logger.info("Read ConnectionManager at config yml {}", new File(ChassisConfig.ConnectionConfig.DEFAULT_CONNECTION_PATH.get()).getAbsolutePath());
			configuration = YamlUtils.objectFromYaml(ConnectionConfiguration.class, ChassisConfig.ConnectionConfig.DEFAULT_CONNECTION_PATH.get());
			logger.info("[Parsing user connection configuration]\n" + YamlUtils.objectToPrettyYaml(configuration));
		} catch (Throwable e) {
			logger.error("Load user connection configuration failed", e);
			logger.info(baseConfiguration != null
				? "Run base connection configuration mode"
				: "Run non-mesh mode");
		}
		// overwrite user peers by id
		if (baseConfiguration != null) {
			configuration.clients.addAll(
				baseConfiguration.clients.parallelStream()
					.filter(c -> configuration.clients.parallelStream().noneMatch(preferC -> Objects.equals(preferC.id, c.id)))
					.collect(Collectors.toList()));
			configuration.servers.addAll(
				baseConfiguration.servers.parallelStream()
					.filter(s -> configuration.servers.parallelStream().noneMatch(preferS -> Objects.equals(preferS.id, s.id) || Objects.equals(preferS.port, s.port)))
					.collect(Collectors.toList()));
		}
	}

	public void setConfiguration(ConnectionConfiguration conf, Consumer<ConnectionConfiguration> onConnectionConfigNotNull) {
		logger.info("Set ConnectionManager");
		configuration = conf;
		applyConfig(onConnectionConfigNotNull);
	}

	public void setConfiguration(ConnectionConfiguration conf) {
		setConfiguration(conf, null);
	}

	public void loadYmlConfig(Consumer<ConnectionConfiguration> onConnectionConfigNotNull) throws Exception {
		readConfigYml();
		// let error throw to app
		applyConfig(onConnectionConfigNotNull);
	}

	public void loadYmlConfig() throws Exception {
		loadYmlConfig(null);
	}

	private void applyConfig(Consumer<ConnectionConfiguration> onConnectionConfigNotNull) {
		if (configuration != null) {
			try {
				if (onConnectionConfigNotNull != null) onConnectionConfigNotNull.accept(configuration);
				init();
				if (configuration.warmup)
					warmUpTcp(ChassisConfig.PerformanceConfig.WarmupConfig.TCP_WARMUP_SIZE.getInt(), ChassisConfig.PerformanceConfig.WarmupConfig.TCP_WARMUP_COUNT.get());
			} catch (Throwable e) {
				logger.error("Microchassis.ConnectionManager.loadYmlConfig compose failed, continue to run without init", e);
			}
		}
	}

	public LBRAdapter getNegtor(String id) {
		if (logger.isDebugEnabled()) logger.debug("Find negtor {}/{}", id, mapLbrClient.keySet());
		LBRAdapter negtor;
		if((negtor = mapLbrClient.get(id)) == null) return null;
		return negtor.waitInit();
	}


	/**
	 * ConnectionManager serve only server config
	 */
	static <T extends PeerConfig> Stream<T> filterIds(Stream<T> inputStream, Pattern idRegex) {
		return inputStream.filter(conf -> idRegex.matcher(conf.id).matches());
	}

	static <T extends PeerConfig> T filterId(Stream<T> inputStream, String id) {
		return inputStream.filter(conf -> Objects.equals(conf.id, id)).findFirst().orElse(null);
	}

	public HttpServerOptions asHttpConfig(ServerConfiguration conf) {
		return conf instanceof HttpServerOptions
			? (HttpServerOptions) conf
			: null;
	}

	public HttpClientOptions asHttpConfig(ClientConfiguration conf) {
		return conf instanceof HttpClientOptions
			? (HttpClientOptions) conf
			: null;
	}

	public ClientConfiguration getClientConfig(String id) {
		return filterId(configuration.clients.stream(), id);
	}

	public ServerConfiguration getServerConfig(String id) {
		return filterId(configuration.servers.stream(), id);
	}

	public Stream<ServerConfiguration> getServerConfigStream(Pattern regex) {
		return filterIds(configuration.servers.stream(), regex);
	}

	public Stream<ClientConfiguration> getClientConfigStream(Pattern regex) {
		return filterIds(configuration.clients.stream(), regex);
	}

	public List<ClientConfiguration> getClientConfig(Pattern regex) {
		return getClientConfigStream(regex).collect(Collectors.toList());
	}

	public List<ServerConfiguration> getServerConfig(Pattern regex) {
		return getServerConfigStream(regex).collect(Collectors.toList());
	}

	public List<HttpServerOptions> getHttpServerConfig(Pattern regex) {
		return getServerConfigStream(regex).map(this::asHttpConfig).filter(Objects::nonNull).collect(Collectors.toList());
	}

	public List<HttpClientOptions> getHttpClientConfig(Pattern regex) {
		return getClientConfigStream(regex).map(this::asHttpConfig).filter(Objects::nonNull).collect(Collectors.toList());
	}

	public HttpServerOptions getHttpServerConfig(String id) {
		return asHttpConfig(getServerConfig(id));
	}

	public HttpClientOptions getHttpClientConfig(String id) {
		return asHttpConfig(getClientConfig(id));
	}

	public <C extends MainSite> C getClient(String id) {
		try {
			return (C) (mapClient.get(id));
		} catch (ClassCastException e) {
			return null;
		}
	}

	public <S extends NettyServer> S getServer(String id) {
		try {
			return (S) (mapServer.get(id));
		} catch (ClassCastException e) {
			return null;
		}
	}

	private <P extends IDfy, Q> Stream<Q> filterPeer(Stream<P> inputStream, Pattern regex) {
		return inputStream.filter(p -> regex.matcher(p.getId()).matches()).map(p -> {
			try {
				return (Q) p;
			} catch (ClassCastException e) {
				return null;
			}
		}).filter(Objects::nonNull);
	}

	public <C extends MainSite> Stream<C> getClients(Pattern regex) {
		return filterPeer(mapClient.values().stream(), regex);
	}

	public <S extends NettyServer> Stream<S> getServers(Pattern regex) {
		return filterPeer(mapServer.values().stream(), regex);
	}

	public void initWithoutConnConfig() {
		logger.info("Init ConnectionManager without config");
		if (!CommonConfig.JavaConfig.tasksetCpus.check().isEmpty()) TasksetUtils.setupAffinity();
		startMonitorThread();
	}

	/**
	 * ConnectionMgr co the duoc goi sau NettyClient va NettyServer, nen lan resolve dau tien cua NettyClient/Server se khong ket noi duoc
	 */
	public void init() {
		try {
			logger.info("[Parsing final connection configuration]\n" + YamlUtils.objectToPrettyYaml(configuration));
			Metric.disableCollector.set(!configuration.metricEnable);
			if (!CommonConfig.JavaConfig.tasksetCpus.check().isEmpty() && configuration.clients.stream().anyMatch(c -> c.channelConfiguration.affinity))
				TasksetUtils.setupAffinity(); // if declared tasksetCpus and any client use affinity, then set
			// need call before client and server constructor
			Set<String> allLbrModes = new CopyOnWriteArraySet<>();
			allLbrModes.addAll(configuration.clients.stream().map(cconf -> cconf.connectionLimitMode).filter(Objects::nonNull).collect(Collectors.toSet()));
			allLbrModes.addAll(configuration.servers.stream().map(sconf -> sconf.connectionLimitMode).filter(Objects::nonNull).collect(Collectors.toSet()));
			if (configuration.lbrs != null) {
				for (LBRClientConfiguration lbrConfig : configuration.lbrs) {
					if (allLbrModes.contains(lbrConfig.type)) {
						switch (lbrConfig.type) {
							case LBRClientConfiguration.CENTER_LBR_CLIENT:
								logger.info("building LBR instance Center");
								mapLbrClient.putIfAbsent(lbrConfig.getId(), new CenterLBR(lbrConfig));
								break;
							case LBRClientConfiguration.STANDALONE_LBR_CLIENT:
							default:
								logger.info("building LBR instance Standalone");
								mapLbrClient.putIfAbsent(lbrConfig.getId(), new StandaloneLBR(lbrConfig));
						}
					}
				}
			}
			configuration.clients.stream()
				.filter(conf -> Objects.equals(conf.id, ChassisConst.defaultHttpClientId) && conf instanceof HttpClientOptions)
				.forEach(conf -> {
					try {
						defaultHttpInstance = new HttpInstanceClient((HttpClientOptions) conf);
					} catch (
						Throwable e) {
						logger.error(e, e);
					}
				});
			if (defaultHttpInstance == null)
				defaultHttpInstance = HttpInstanceClient.createInstance(ChassisConst.defaultHttpClientId);
			// init defaultHttpClient first before init other HttpClient, because constructor of HttpClient need HealthcheckMonitor using defaultHttpClient

			for (ClientConfiguration conf : configuration.clients) {
				if (conf instanceof HttpClientOptions) {
					HttpClientOptions hconf = (HttpClientOptions) conf;
					if (!hconf.instance) {
						if(hconf.initProto.equals(ChassisConst.SupportVersion.HTTP1_1) || hconf.initProto.equals(ChassisConst.SupportVersion.HTTP1_0)){
							if (hconf.negotiable) hconf.newClientHttp1Upgrade();
							else hconf.newClientHttp1Only();
						} else if(hconf.initProto.equals(ChassisConst.SupportVersion.HTTP2_0)){
							if (hconf.negotiable) hconf.newClientHttp2Fallback();
							else hconf.newClientHttp2Only();
						}
					} // else skip init instance hclients
					// if(Objects.equals(hconf.id, ChassisConst.ManoConst.manoHttpClientId){ // ignore instance http client of id=manoClient
					// if (Objects.equals(hconf.id, ChassisConst.defaultHttpClientId)) // init instance http client of id=defaultHttpClient
				} else new TcpClient(conf);
			}
			for (ServerConfiguration serverOptions : configuration.servers) {
				HttpServerOptions httpConfig = asHttpConfig(serverOptions);
				if (httpConfig != null) new HttpServer(httpConfig);
				else new TcpServer(serverOptions);
			}
		} catch (Throwable e) {
			logger.error(e, e);
		}

		startMonitorThread();
	}

	public void shutDown() {
		logger.info("[Connection Manger is shutting down...]");
		streamNettyClients(true).forEach(nettyClient -> {
			try {
				nettyClient.close();
			} catch (Exception e) {
				logger.error(e, e);
			}
		});

		mapClient.clear();
		logger.info("[All client closed and removed]");
		mapServer.forEach((k, v) -> {
			try {
				v.stop();
			} catch (Exception e) {
				logger.error(e, e);
			}
		});
		mapServer.clear();
		logger.info("[All server closed and removed]");
		stopMonitorThread();
	}

	public void stopMonitorThread() {
		try {
			RequestTimeoutMonitor.stop();
			ThreadManager.JobMonitor.shutDown(30);
			ThreadManager.sharedThreadPoolExecutor.stop();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}


	public synchronized void startMonitorThread() {
		if (monitorStarted.get()) {
			logger.info("Monitor thread already started");
			return;
		}
		ThreadManager.JobMonitor.scheduleJob(new ConnectionStatusLoggingJob());
		ThreadManager.JobMonitor.scheduleJob(new BufferStatus());
		ThreadManager.JobMonitor.scheduleJob(new ChannelMonitorStatus());
		monitorStarted.set(true);
	}

	private class BufferStatus implements ThreadManager.JobMonitor.Job {
		@Override
		public String getId() {
			return "Buffer status";
		}

		@Override
		public Runnable getJob() {
			return () -> {
				if (!mapServer.isEmpty()) {
					logger.debug("==========SERVER BUFFER ALLOCATOR==========");
					mapServer.values().forEach(server -> {
						ByteBufAllocator bytebufAllocator = server.getBytebufAllocator();
						logger.debug("{}:{}", server.getId(), (server.getConfig().channelConfiguration.pooledByteBuffer ? bytebufAllocator.toString() : ((PooledByteBufAllocator) bytebufAllocator).metric()));
						logger.info(server.getConnectionStat());
					});
				}
				if (!mapClient.isEmpty()) {
					logger.debug("==========CLIENT BUFFER ALLOCATOR==========");
					mapClient.values().forEach(client -> {
						ByteBufAllocator bytebufAllocator = client.getBytebufAllocator();
						logger.debug("{}:{}", client.getId(), (client.getConfig().channelConfiguration.pooledByteBuffer ? bytebufAllocator.toString() : ((PooledByteBufAllocator) bytebufAllocator).metric()));
					});
				}
			};
		}

		@Override
		public int getInitialDelay() {
			return 10;
		}

		@Override
		public int getInterval() {
			return CommonConfig.ThreadConfig.LOG_BUFFER_CONFIG.INTERVAL_DURATION.getInt();
		}

		@Override
		public TimeUnit getTimeUnit() {
			return CommonConfig.ThreadConfig.LOG_BUFFER_CONFIG.TIME_UNIT;
		}
	}

	private class ConnectionStatusLoggingJob implements ThreadManager.JobMonitor.Job {

		public ConnectionStatusLoggingJob() {
			//emty
		}

		@Override
		public String getId() {
			return "Log connection status";
		}

		@Override
		public Runnable getJob() {
			return () -> {
				streamNettyClients(true).forEach(client -> {
					try {
						client.logConnectionStat();
					} catch (Exception ex) {
						logger.error(ex, ex);
					}
				});
				mapServer.values().forEach(server -> {
					ServerCounter counter = server.getCounter();
					try {
						logger.info("[Server current processing status]{id={}, request={}, response={}, release={}, concurrent={}}",
							server.getId(),
							counter.getReceived(),
							counter.getSent(),
							counter.getReleased(),
							counter.getSentCC());
					} catch (Exception ex) {
						logger.error(ex, ex);
					}
				});
				RequestTimeoutMonitor.logStatus();
				ThreadManager.sharedThreadPoolExecutor.logStatus();
				ThreadManager.JobMonitor.logStatus();
			};
		}

		@Override
		public int getInitialDelay() {
			return CommonConfig.ThreadConfig.LOG_STATUS_CONFIG.DELAY_DURATION.getInt();
		}

		@Override
		public int getInterval() {
			return CommonConfig.ThreadConfig.LOG_STATUS_CONFIG.INTERVAL_DURATION.getInt();
		}

		@Override
		public TimeUnit getTimeUnit() {
			return CommonConfig.ThreadConfig.LOG_STATUS_CONFIG.TIME_UNIT;
		}
	}

	private static class ServiceDiscoveryJob implements ThreadManager.JobMonitor.Job {

		@Override
		public String getId() {
			return "Service discovery";
		}

		@Override
		public Runnable getJob() {
			return ServiceMonitor.job;
		}

		@Override
		public int getInitialDelay() {
			return CommonConfig.ThreadConfig.SERVICE_DISCOVERY_CONFIG.DELAY_DURATION.getInt();
		}

		@Override
		public int getInterval() {
			return CommonConfig.ThreadConfig.SERVICE_DISCOVERY_CONFIG.INTERVAL_DURATION.getInt();
		}

		@Override
		public TimeUnit getTimeUnit() {
			return CommonConfig.ThreadConfig.SERVICE_DISCOVERY_CONFIG.TIME_UNIT;
		}
	}

	private static class ChannelMonitorStatus implements ThreadManager.JobMonitor.Job {
		@Override
		public String getId() {
			return "channel monitor status";
		}

		@Override
		public Runnable getJob() {
			return () -> logger.info(ChannelMonitor.getInstance().toString());
		}

		@Override
		public int getInitialDelay() {
			return CommonConfig.ThreadConfig.LOG_STATUS_CONFIG.INTERVAL_DURATION.getInt();
		}

		@Override
		public int getInterval() {
			return CommonConfig.ThreadConfig.LOG_STATUS_CONFIG.DELAY_DURATION.getInt();
		}

		@Override
		public TimeUnit getTimeUnit() {
			return CommonConfig.ThreadConfig.LOG_STATUS_CONFIG.TIME_UNIT;
		}

	}

//	public static void main(String[] args) throws Exception {
//		ChassisConfig.ConnectionConfig.DEFAULT_CONNECTION_FILENAME.setDefault("connection-tien.yml");
//		ConnectionConfiguration configuration = YamlUtils.objectFromYaml(ConnectionConfiguration.class, ChassisConfig.ConnectionConfig.DEFAULT_CONNECTION_PATH.get());
//		System.out.println("Suport: " + ((HttpServerOptions) configuration.servers.get(0)).getSupportedProtocolNames() + " max2L " + ((Http2_0VersionConfigures) (((HttpServerOptions) configuration.servers.get(0)).getSupport(ChassisConst.SupportVersion.HTTP_2_0))).maxContentLength);
//	}
}

