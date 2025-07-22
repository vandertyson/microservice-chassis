package com.viettel.vocs.mano.service;

import com.google.gson.reflect.TypeToken;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.file.YamlUtils;
import com.viettel.vocs.common.os.thread.SharedThreadPoolManager;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpAPI;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.RequestTimeoutMonitor;
import com.viettel.vocs.microchassis.connection.client.ChannelMonitor;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.ConnectionConfiguration;
import com.viettel.vocs.microchassis.connection.config.IsolationConfig;
import com.viettel.vocs.microchassis.connection.dns.ServiceMonitor;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.MainSite;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.client.HealthCheckMonitor;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class ConnectionHandler extends HttpAPI {
	private static final Logger logger = LogManager.getLogger(ConnectionHandler.class);
	static Type mapType = new TypeToken<Map<String, String>>() {
	}.getType();
	private static final Map<String, Handler<Pair<Map<String, String>, MainSite>>> CLIENT_CONFIGURABLES = new ConcurrentHashMap<>();

	static {
		CLIENT_CONFIGURABLES.put(ChassisConst.SENDTIMEOUTMS, arg0 -> {
			try {
				String s = arg0.getKey().get("client.sendTimeoutMs");
				if (s != null) {
					arg0.getValue().getConfig().setSendTimeoutMs(Long.parseLong(s));
				}
			} catch (Exception ex) {
				logger.error(ex, ex);
			}
		});
		CLIENT_CONFIGURABLES.put(ChassisConst.ISOLATION_INTERVAL_MS, arg0 -> {
			try {
				Map<String, String> param = arg0.getKey();
				ClientConfiguration config = arg0.getValue().getConfig();
				String interval = param.get(ChassisConst.ISOLATION_INTERVAL_MS);
				String minCount = param.get(ChassisConst.ISOLATION_MIN_REQUEST_COUNT);
				String percent = param.get(ChassisConst.ISOLATION_THRESHOLD_PERCENT);
				if (interval != null && minCount != null && percent != null) {
					return;
				}
				IsolationConfig isolationConfig = config.getIsolationConfig() != null
					? config.getIsolationConfig()
					: new IsolationConfig();
				if (config.getIsolationConfig() == null) {
					config.setIsolationConfig(isolationConfig);
				} else {
					if (interval != null && interval.equals("0")) {
						config.setIsolationConfig(null);
					}
				}
				if (minCount != null) {
					isolationConfig.timeoutConfig.monitorSpeed = Math.round(Integer.parseInt(minCount) * 1000f / Long.valueOf(interval));
				}
				if (percent != null) {
					isolationConfig.timeoutConfig.thresholdPct = Integer.parseInt(percent);
				}
			} catch (Exception ex) {
				logger.error(ex, ex);
			}
		});
		CLIENT_CONFIGURABLES.put(ChassisConst.CLIENT_WARMUP_ENABLE, arg0 -> {
			try {
				ConnectionManager.getInstance().getConfiguration().warmup = Boolean.parseBoolean(arg0.getKey().get(ChassisConst.CLIENT_WARMUP_ENABLE));
			} catch (Exception ex) {
				logger.error(ex, ex);
			}
		});
		CLIENT_CONFIGURABLES.put(ChassisConst.WARMUP_TPS_STEPS, arg0 -> {
			try {
				String s = arg0.getKey().get(ChassisConst.WARMUP_TPS_STEPS);
				arg0.getValue().getConfig().setWarmupTpsSteps(s);
			} catch (Exception ex) {
				logger.error(ex, ex);
			}
		});
	}

	@Override
	public void handle(HttpServerContext ctx) {
		Map<String, List<String>> queryStringParams = ctx.getInParams();
		boolean pool = queryStringParams.containsKey("pool");
		List<String> stat = queryStringParams.get("stat");
		boolean config = queryStringParams.containsKey("config");
		if (stat != null && !stat.isEmpty()) {
			boolean detail = queryStringParams.containsKey("detail");
			Map<String, Object> result = new HashMap<>();
			if (stat.get(0).trim().isEmpty() || stat.get(0).isBlank()) {
				stat = Arrays.asList("client", "server", "ping", "sd", "timeout", "tcpDrop");
			}
			for (String s : stat) {
				try {
					switch (s.trim()) {
						case "config":
							ConnectionConfiguration configuration = ConnectionManager.getInstance().getConfiguration();
							String s2 = YamlUtils.objectToPrettyYaml(configuration);
							result.put(s, s2);
							break;
						case "client":
							result.put(s, ConnectionManager.mapClient.values().stream().map(MainSite::toString).collect(Collectors.joining("\n")));
							break;
						case "server":
							result.put(s, ConnectionManager.mapServer.values().stream().map(NettyServer::toString).collect(Collectors.joining("\n")));
							break;
						case "buffer":
							StringBuilder sb = new StringBuilder();
							if (!ConnectionManager.mapServer.isEmpty()) {
								sb.append("==========SERVER BUFFER ALLOCATOR==========\n")
									.append(ConnectionManager.mapServer.values().stream().map(f -> {
										ByteBufAllocator bytebufAllocator = f.getBytebufAllocator();
										return f.getConfig().id + ":" + (
											bytebufAllocator instanceof PooledByteBufAllocator ? ((PooledByteBufAllocator) bytebufAllocator).metric() : bytebufAllocator.toString()
										);
									}).collect(Collectors.joining("\n")));
							}
							if (!ConnectionManager.mapClient.isEmpty()) {
								sb.append("\n==========CLIENT BUFFER ALLOCATOR==========\n")
									.append(ConnectionManager.mapClient.values().stream().map(f -> {
										ByteBufAllocator bytebufAllocator = f.getBytebufAllocator();
										return f.getConfig().id + ":" + (
											bytebufAllocator instanceof PooledByteBufAllocator ? ((PooledByteBufAllocator) bytebufAllocator).metric() : bytebufAllocator.toString()
										);
									}).collect(Collectors.joining("\n")));
							}
							result.put(s, sb.toString());
							break;
						case "ping":
							result.put(s, ChannelMonitor.getInstance().toString());
							break;
						case "sd":
							result.put(s, ServiceMonitor.report());
							break;
						case "timeout":
							result.put(s, RequestTimeoutMonitor.report());
							break;
						case "hc":
							result.put(s, HealthCheckMonitor.instance.toString());
							break;
						case "shared":
							result.put(s, SharedThreadPoolManager.report());
							break;
					}
				} catch (Exception ex) {
					result.put(s, ex.getMessage());
				}
			}
			String s = YamlUtils.objectToPrettyYaml(result);
			ctx.send(s);
			return;
		}
		if (pool) {
			List<String> action = queryStringParams.get("action");
			String s = SharedThreadPoolManager.report();
			if (action != null && !action.isEmpty()) {
				String act = action.get(0);
				if (act.equalsIgnoreCase("clear")) {
					SharedThreadPoolManager.clearMapProgress();
					ctx.send(s);
					return;
				}
				if (act.equalsIgnoreCase("purge")) {
					ThreadManager.sharedThreadPoolExecutor.purgeJobQueue();
					ctx.send(s);
					return;
				}
				if (act.equalsIgnoreCase("empty")) {
					ThreadManager.sharedThreadPoolExecutor.clearJobQueue();
					ctx.send(s);
					return;
				}
				ctx.send("Invalid action. Available actions ['clear','purge', 'empty']");
			} else {
				ctx.send(s);
			}
			return;
		}
		if (config) {
			try {
				String s = ctx.getInMsg().toString(CharsetUtil.UTF_8);
				Map<String, String> configs = JsonUtils.getDecoder().fromJson(s, mapType);
				logger.info("Connection config received.\n{}", configs);
				ctx.send(handleConnectionConfig(configs));
			} catch (Exception ex) {
				ctx.send(ex.toString(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
				logger.error(ex, ex);
			}
			return;
		}
		handleStatCheck(ctx);
	}

	private String handleConnectionConfig(Map<String, String> configs) {
		String type = configs.get("type");
		Map<String, Object> affected = new HashMap<>();
		if (type.equals("client")) {
			String ids = configs.get("ids");
			if (ids == null || ids.isEmpty()) {
				return "Client id is required. Available: " + ConnectionManager.mapClient.keySet();
			}
			String[] clients = ids.split(",");
			for (String split : clients) {
				String client = split.trim();
				ClientConfiguration config = ConnectionManager.getInstance().getClientConfig(client);
				if (config == null) continue;
				try {
					boolean changed = false;
					for (Map.Entry<String, Handler<Pair<Map<String, String>, MainSite>>> entry : CLIENT_CONFIGURABLES.entrySet()) {
						if (configs.containsKey(entry.getKey())) {
							entry.getValue().handle(new ImmutablePair(configs, entry.getValue()));
							changed = true;
						}
					}
					if (changed) {
						affected.put(client, config);
					}
				} catch (Exception ex) {
					logger.error(ex, ex);
				}

			}
		}
		if (type.equals("server")) {
			String ids = configs.get("ids");
			if (ids == null || ids.isEmpty()) {
				return "Server id is required. Available: " + ConnectionManager.mapServer.keySet();
			}
		}
		return YamlUtils.objectToPrettyYaml(affected);
	}

	private void handleStatCheck(HttpServerContext ctx) {
		try {
			ctx.send(YamlUtils.objectToPrettyYaml(ConnectionManager.streamNettyClients(false).collect(Collectors.toMap(Site::getId, NettyClient::getClientStatus))));
		} catch (Exception e) {
			logger.error(e, e);
			ctx.send(e.getMessage(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
		}
	}

	public static void main(String[] args) {
		String url = "http://172.20.3.69:9000/chassis/connection?stat";
		QueryStringDecoder qsd = new QueryStringDecoder(url);
		List<String> stat = qsd.parameters().get("stat");
		System.out.println(stat + " " + stat.size() + " " + stat.get(0).isEmpty());
	}
}
