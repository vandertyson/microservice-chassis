package com.viettel.vocs.mano.service;

import com.google.gson.reflect.TypeToken;
import com.viettel.vocs.common.config.ConfigAPI;
import com.viettel.vocs.common.datatype.BooleanToggle;
import com.viettel.vocs.common.file.JsonParser;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.mano.model.BlueGreenUpdate;
import com.viettel.vocs.mano.model.ConfigAPIResponse;
import com.viettel.vocs.mano.reload.APIReloadManager;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpAPI;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.client.InstanceClient;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import com.viettel.vocs.microchassis.connection.config.ConnectionConfiguration;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.ManoConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.BlueGreenUpdateSiteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.connection.server.ServerAPI;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.topology.TopoHandler;
import com.viettel.vocs.microchassis.topology.model.InstanceTopo;
import com.viettel.vocs.microchassis.topology.model.LinkInfo;
import com.viettel.vocs.microchassis.topology.model.TopoLink;
import com.viettel.vocs.microchassis.topology.model.TopoNode;
import com.viettel.vocs.microchassis.tracing.client.TracingMonitor;
import com.viettel.vocs.microchassis.tracing.utils.TraceHelper;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public interface ManoHandler extends TopoHandler {

	Logger logger = LogManager.getLogger(ManoHandler.class);
	AtomicLong rejectedUDA = new AtomicLong();
	AtomicLong acceptedUDA = new AtomicLong();
	ThreadManager.PoolExecutor manoThreadPoolExecutor = ChassisConfig.ManoConfig.MANO_UDA_CONFIG.makeExecutor((r, executor) -> {
		if (r instanceof ManoUDAHandler) {
			logger.error("Reject UDA execution. count={}. uda={}", rejectedUDA.incrementAndGet(), r.toString());
		} else {
			logger.error("Reject UDA execution. count={}", rejectedUDA.incrementAndGet());
		}
	});

	AtomicBoolean bypassHealthcheck = new AtomicBoolean(ChassisConfig.HealthcheckConfig.HEALTH_CHECK_BYPASS.get());

	default void registerMtopoAPIs(Mano1Handler topoHandler) {
		topoHandler.registerApiExact("/topo/config", new HttpAPI() {
			private Map<String, TopoLink> topoConnectionYmlDiscover(Map<String, Site> mapSite, Map<String, NettyServer> mapServer) {
				Map<String, TopoLink> linkList =
					mapServer.values().parallelStream()
						.map(server -> {
							TopoNode serverNode = new TopoNode(server.getId(), true);
							mTopoLocal.get().addNodes(serverNode);
							return (((ServerAPI) server).getConnectedEndpointPair()).entrySet().parallelStream().map(entry -> {
								NettyServer.ServerLinkAddress addressPair = entry.getKey();
								Integer channelCount = entry.getValue();
								LinkInfo linkInfo = new LinkInfo(String.format("%s_%s_%s", server.getId(), addressPair.serverE.toString(), addressPair.clientIp), Endpoint.newEndpoint(addressPair.clientIp, 0), addressPair.serverE, serverNode);
								//					Map<Endpoint, Long> mapEndpoint = server.countEndpoints();
								linkInfo.connected = channelCount;
								linkInfo.desired = server.getMesh().minConnection; // server has no desired of client conn
								return new TopoLink(serverNode, linkInfo);
							}).collect(Collectors.toList());
						})
						.flatMap(Collection::stream).collect(Collectors.toMap(l -> l.id, l -> l));
				linkList.putAll(
					mapSite.values().parallelStream()
						.map(site -> {
							TopoNode clientNode = new TopoNode(site.getId(), false);
							mTopoLocal.get().addNodes(clientNode);
							List<TopoLink> linksOfSite = new ArrayList<>();
							Map<Endpoint, Map<Endpoint /*serverE*/, AtomicInteger/*connected*/>> linkEndpoints = new HashMap<>();
							((Set<Routable>) site.getConnections()).parallelStream().filter(Routable::isConnected).map(routable -> { // String /*IP*/, Long count
								Endpoint fromE = Endpoint.local(routable.getChannel()); // hasIp
								fromE = fromE != null ? fromE.patchServiceName(ConnectionManager.getMyDNS()) : null; // patch my DNS to source for identifiable
								Endpoint toE = routable.getEndpoint().patchServiceName(site.getEndpoint().serviceName);
								return Pair.of(fromE, toE);
							}).forEach(p -> linkEndpoints.computeIfAbsent(p.getKey(), k -> new HashMap<>()).computeIfAbsent(p.getValue(), k -> new AtomicInteger(0)).incrementAndGet());
							linkEndpoints.forEach((srcE, mapDest) -> mapDest.forEach((destE, connectedCount) -> {
								LinkInfo linkInfo = new LinkInfo(String.format("%s_%s_%s", site.getMonitorID(), srcE, destE), srcE, destE, clientNode);
								linkInfo.connected = connectedCount.get();
								Map<String, Long> destMeshMap = site.getMesh().destinations;
								Long desired = destMeshMap != null ? destMeshMap.get(destE.ip) : null;
								linkInfo.desired = destE.ip != null && desired != null && desired != 0 ? desired : site.getMesh().minConnection; // server has no desired of client conn
								linksOfSite.add(new TopoLink(clientNode, linkInfo));
							}));
							return linksOfSite;
						})
						.flatMap(Collection::stream).collect(Collectors.toMap(l -> l.id, l -> l)));
				return linkList;
			}

			@Override
			public void getHandle(HttpServerContext serverCtx) {
				InstanceTopo graph = new InstanceTopo(getBusiness(), getInfra(), getVirt());
				mTopoLocal.set(graph);
				Map<String, TopoLink> links = topoHandler.topoExtLinkDiscover(graph); // null safe
				if (links != null) graph.addLinks(links.values());

				graph.addLinks(topoConnectionYmlDiscover(
					ConnectionManager.mapClient.values().parallelStream().map(
							mainSite -> mainSite instanceof InstanceClient
								? List.of(Pair.of(mainSite.getId(), (InstanceClient) mainSite))
								: ((Set<Site>) (((NettyClient) mainSite).getUpdater().getDestMap().values().stream().collect(Collectors.toSet())))
								.stream().map(s -> Pair.of(s.getMonitorID(), s))
								.collect(Collectors.toList()))
						.flatMap(Collection::stream)
						.collect(Collectors.toMap(Pair::getKey, Pair::getValue)),
					ConnectionManager.mapServer).values());

				serverCtx.send(JsonUtils.getEncoder().toJson(Map.of(
					"nodes", graph.nodes,
					"links", graph.links,
					"addresses", Map.of(
						"businessAddress", graph.businessAddress,
						"virtAddress", graph.virtAddress,
						"infraAddress", graph.infraAddress))));
			}
		});
	}

	static void chassisBlueGreenUpdate(HttpServerContext hctx, List<NettyClient> subScribedManoClients, BlueGreenUpdate blueGreenUpdate) {
		String serviceName = blueGreenUpdate.green_service_name;
		//check step de biet la dang o buoc nao
		//neu la buoc pre-update: check xem co client nao dang ket noi den service name ko. neu co tra 200 OK. neu khong tra 500
		ManoConfigure manoBGconfig = enableManoControlFor(serviceName, subScribedManoClients);
		switch (blueGreenUpdate.type) {
			case PRE_UPDATE:
			case PRE_UPGRADE:
				break;
			default:
				//neu la buoc updating/upgrading hoac updated/upgraded
				if (BlueGreenUpdate.Status.FAILED.equals(blueGreenUpdate.result))
					blueGreenUpdate.weight = 0; /**
			 * case weight B0/G100 green chet het -> khong co resolvedIPs green -> khong gui duoc vao IP nao, blue weight 0 cung khong gui vao,
			 *  ngam tai tren queue. Sau do mano gui FAILED, tai se ve B100/G0
			 */
				if (manoBGconfig != null) {
					String pairHostname = BlueGreenUpdateSiteStrategy.createPairHostname(serviceName);
					Map<String, Integer> weightMap = new ConcurrentHashMap<>();
					weightMap.put(blueGreenUpdate.green_service_name, blueGreenUpdate.weight);
					weightMap.put(pairHostname, 100 - blueGreenUpdate.weight);
					manoBGconfig.setIWeightMap(weightMap);
					if (BlueGreenUpdate.Type.UPDATED.equals(blueGreenUpdate.type) ||
						BlueGreenUpdate.Type.UPGRADED.equals(blueGreenUpdate.type)) {
						subScribedManoClients.stream()
							.filter(nettyClient -> Objects.equals(nettyClient.getHostName(), pairHostname))
							.forEach(NettyClient::disableUpdateControl);
					}
				}
				break;
		}
		hctx.send(manoBGconfig != null
			? HttpResponseStatus.OK
			: HttpResponseStatus.INTERNAL_SERVER_ERROR);
	}

	static ManoConfigure enableManoControlFor(String
																							serviceName, List<NettyClient> subScribedManoClients) {
		List<ManoConfigure> manoBGconfigs = subScribedManoClients.parallelStream()
			.filter(nettyClient -> Objects.equals(nettyClient.getHostName(), BlueGreenUpdateSiteStrategy.createPairHostname(serviceName)))
			.map(NettyClient::createManoConfigAndEnableManoControl)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		// 1 service name search co the co nhieu netty client nhung chi co 1 manoBGconfig
		return manoBGconfigs.size() == 0 ? null : manoBGconfigs.get(0);
	}

	//	static abstract class NC {
//	}
//
//	static class TcpC extends NC {
//	}
//	static Map<String, NC> mapCl = new ConcurrentHashMap<>();
//	static <S extends NC> S getNc(String s){
//		try {
//			return (S) (mapCl.get(s));
//		} catch (ClassCastException e) {
//			return null;
//		}
//	}
//	public static void main(String[] args) {
//		mapCl.put("tcp", new TcpC());
//		TcpC tcp = Objects.requireNonNull(getNc("tcp"), "failed null found");
//		System.out.printf("tcp is %s class off %s, is TcpC %b\n", tcp, tcp.getClass().getSimpleName(), tcp instanceof TcpC);
//	}
	private static void registerFileConnectionExport() {
		ConfigAPI.register(new ConfigAPI() {
			@Override
			public String getSubPath() {
				return "microchassis/connection";
			}

			@Override
			public String getConfigName() {
				return "microchassis-connection";
			}

			@Override
			public Pair<HttpResponseStatus, String> exportConfig() {
				try {
					ConnectionConfiguration connectionConfiguration = ConnectionManager.getInstance().getConfiguration();
					return Pair.of(HttpResponseStatus.OK, connectionConfiguration != null ? JsonParser.getInstance().writeAsJsonString(connectionConfiguration) : "not yet loaded");
				} catch (Throwable e) {
					return Pair.of(HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
				}
			}
		});
	}


	default void registerManoAPIs(HttpServerHandler manoHandler) {
		MetricHandler metricHandler = new MetricHandler(hctx -> {
			if (MetricHandler.pullCheck(hctx)) metrics(hctx);
		});
		AlarmHandler alarmHandler = new AlarmHandler();
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.TRACING_CONFIG.get(), new HttpAPI() {
			@Override
			public void postHandle(HttpServerContext hctx) {
				String content = hctx.getInMsg().toString(StandardCharsets.UTF_8);
				logger.info("[Tracing config update]{ctx={}, headers={}, payload={}}", hctx.getChannelString(), hctx.getInMsg().headers(), content);
				Pair<HttpResponseStatus, String> result = TracingMonitor.getInstance().updateParameter(TraceHelper.getParameterReq(content));
				hctx.send(HttpResponseStatus.OK.equals(result.getKey()) ? TracingMonitor.getTracingConfig() : result.getValue(), result.getKey());
			}

			@Override
			public void putHandle(HttpServerContext hctx) {
				Map<String, List<String>> params = hctx.getInParams();
				logger.info("[Tracing config modify]{ctx={}, isHttp2={}}", hctx.getChannelString(), hctx.isHttp2());
				List<String> max = params.get("max");
				if (max != null && !max.isEmpty()) {
					int val = Integer.parseInt(max.get(0));
					TracingMonitor.getInstance().setMaxTracingSize(val);
					hctx.send("max tracing object is set to " + val);
				}
				hctx.send(HttpResponseStatus.NO_CONTENT);
			}
		});
		manoHandler.registerApiExact(ChassisConst.ManoConst.reloadAPIPath, new HttpAPI() {
			@Override
			public void postHandle(HttpServerContext serverCtx) {
				APIReloadManager.getInstance().reloadWithRequest(serverCtx);
			}
		});
		manoHandler.registerApiExact(ChassisConst.ManoConst.getAPIPath, new HttpAPI() {
			@Override
			public void getHandle(HttpServerContext serverCtx) {
				APIReloadManager.getInstance().getSupportedAPIReload(serverCtx);
			}
		});
		registerFileConnectionExport();
		manoHandler.registerApiStartWith(ChassisConst.ManoConst.APIPath.getConfigPath, new HttpAPI() {
			final BooleanToggle isExporting = new BooleanToggle(false);

			void putData(HashMap<String, Object> mapData, String subPath, Object data) {
				String[] urlParts = subPath.split("/");
				Object tmp = data;
				int i = urlParts.length - 1;
				while (i > 0) {
					tmp = Map.of(urlParts[i], tmp);
					i--;
				}
				if (tmp instanceof Map && mapData.containsKey(urlParts[i]) && mapData.get(urlParts[i]) instanceof Map) {
					Map<String, Object> map = new HashMap<>();
					map.putAll((Map<String, Object>) tmp);
					map.putAll((Map<String, Object>) mapData.get(urlParts[i]));
					tmp = map;
				}
				mapData.put(urlParts[i], tmp); // i == 0
			}

			@Override
			public void getHandle(HttpServerContext ctx) {
				if (isExporting.get()) {
					ctx.send(JsonUtils.getEncoder().toJson(new ConfigAPIResponse("Error", "Fail! Processing another request !")), HttpResponseStatus.SERVICE_UNAVAILABLE);
					return;
				}
				isExporting.toggle();
				try {
					logger.info("[getConfigParam] getConfigUrl: {}", ctx.getInPath());
					String subUrl = ctx.getInPath().substring(ChassisConst.ManoConst.APIPath.getConfigPath.length()).trim();
					String[] urlParts = subUrl.split("\\?");
					String subPath = urlParts[0].trim();
					if (subPath.endsWith("/")) {
						subPath = subPath.substring(0, subPath.length() - 1);
					}
					Set<String> lstFilterFields = ctx.getInParams().entrySet().stream().filter(e -> e.getKey().endsWith("fields")).map(Map.Entry::getValue).flatMap(List::stream).collect(Collectors.toSet());
					logger.info("[getConfigParam] subPath: {}; fields: {}", subPath, lstFilterFields);
					Type type = new TypeToken<Map<String, Object>>() {
					}.getType();
					HashMap<String, Object> data = new HashMap<>();
					Collection<ConfigAPI> usingConfigAPI = subPath.isEmpty() ? ConfigAPI.mapConfigAPI.values() : (ConfigAPI.mapConfigAPI.containsKey(subPath) ? List.of(ConfigAPI.mapConfigAPI.get(subPath)) : new ArrayList<>());
					usingConfigAPI.forEach(configAPI -> {
						Pair<HttpResponseStatus, String> pair = configAPI.exportConfig();
						if (!HttpResponseStatus.OK.equals(pair.getLeft())) {
							ctx.send(JsonUtils.getEncoder().toJson(new ConfigAPIResponse("Error", "Export config failed: " + configAPI.getConfigName())), pair.getLeft());
							return;
						}
						if (!lstFilterFields.isEmpty()) {
							Map<String, Object> mapJson = JsonUtils.getDecoder().fromJson(pair.getRight(), type);
							logger.info("[getConfigParam] mapJson: " + mapJson.toString());
							Map<String, Object> mapFilter = lstFilterFields.stream().collect(Collectors.toMap(field -> field, mapJson::get, (a, b) -> b));
							mapFilter.forEach((field, val) -> logger.info("[getConfigParam] field={}, value={}", field, val));
							putData(data, configAPI.getSubPath(), mapFilter);
						} else
							putData(data, configAPI.getSubPath(), JsonUtils.getDecoder().fromJson(pair.getRight(), type));
					});
					if (usingConfigAPI.isEmpty()) {
						ctx.send(JsonUtils.getEncoder().toJson(new ConfigAPIResponse("Error", "The requested resource was not found.")), HttpResponseStatus.NOT_FOUND);
						return;
					}
					String responseStr = JsonUtils.getEncoder().toJson(new ConfigAPIResponse("Success", "Success").setData(data));
					if (logger.isInfoEnabled())
						logger.info(subPath.isEmpty() ? "[getConfigParam All] response: {}" : "[getConfigParam] response: {}", responseStr);
					ctx.send(responseStr);
				} finally {
					isExporting.set(false);
				}
			}

			@Override
			public void serverErrorHandle(HttpServerContext serverCtx, Exception e) {
				try {
					serverCtx.send(JsonUtils.getEncoder().toJson(new ConfigAPIResponse("Error", e.getMessage())), HttpResponseStatus.INTERNAL_SERVER_ERROR);
				} catch (Exception e1) {
					logger.error(e1, e1);
				}
			}
		});
		manoHandler.registerApiExact(ChassisConst.ManoConst.APIPath.getConfigTypePath, new HttpAPI() {
			@Override
			public void getHandle(HttpServerContext ctx) {
				if (logger.isInfoEnabled())
					logger.info("[getConfigType] receive request");
				String responseStr = JsonUtils.getEncoder().toJson(
					new ConfigAPIResponse("Success", "Success")
						.setData(ConfigAPI.mapConfigAPI.keySet().stream()
							.collect(Collectors.toMap(subPath -> ConfigAPI.mapConfigAPI.get(subPath).getConfigName(), subPath -> subPath, (a, b) -> b, HashMap::new))));

				if (logger.isInfoEnabled())
					logger.info("[getConfigType] response: {}", responseStr);
				ctx.send(responseStr);
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.CLIENT_ROUTING.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				List<NettyClient> subScribedManoClients = getSubscribedManoClients();
				String json = hctx.getInMsg().toStringUTF8();
				BlueGreenUpdate blueGreenUpdate = null;
				try {
					blueGreenUpdate = JsonUtils.getDecoder().fromJson(json, BlueGreenUpdate.class);
				} catch (Exception ex) {
					logger.error("Can not parse BlueGreenUpdate. payload={}", json);
					logger.error(ex, ex);
				}
				if (blueGreenUpdate == null)
					throw new NullPointerException("Parse BlueGreenUpdate receives null");
				logger.info("Receive mano update blue green. ctx={}, payload={}, data={}", hctx.getChannelString(), json, blueGreenUpdate);
				if (subScribedManoClients != null && !subScribedManoClients.isEmpty())
					chassisBlueGreenUpdate(hctx, subScribedManoClients, blueGreenUpdate);
				else {
					logger.info("List subScribedManoClients is null or empty. Call user handle");
					blueGreenUpdate(hctx, blueGreenUpdate);
				}
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.BYPASS_HEALTH_CHECK_URL.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				bypassHealthcheck.set(!bypassHealthcheck.get());
				hctx.send(String.valueOf(bypassHealthcheck.get()));
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.INTI_CHECK_LIST_PATH.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				hctx.send(HealthCheckHandler.handleReport(hctx.getInParams()));
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.CHASSIS_STAT_CHECK.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				hctx.send(JsonUtils.getEncoder().toJson(ConnectionManager.streamNettyClients(false).collect(Collectors.toMap(Site::getId, NettyClient::getClientStatus, (a,b)->a))));
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.CHASSIS_THREAD_POOL.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				ManoIntegration.reportThreadPool(hctx);
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.TRACING_CONFIG.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				hctx.send(TracingMonitor.getTracingConfig());
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.TOGGLE_ALARM.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				hctx.send(ManoIntegration.toggleAlarmHandle(hctx.getInParams()));
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.TOGGLE_METRIC.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				MetricHandler.toggleMetricHandle(hctx);
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.CLEAR.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				ManoIntegration.clearHandle(hctx);
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.NOTIFY.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				ManoIntegration.onInstance(instance -> ManoIntegration.notification(HttpAPI.makeOnResultStringCallback(hctx)));
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.AUTHEN.get(), new HttpAPI() {
			@Override
			public void handle(HttpServerContext hctx) {
				ManoIntegration.onInstance(instance -> ManoIntegration.authen(HttpAPI.makeOnResultStringCallback(hctx)));
			}
		});
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.TRACING_PATH.get(), new TracingHandler());
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.MANO_STATUS_PATH.get(), new ManoActionHandler());
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.CONNECTION_PATH.get(), new ConnectionHandler());
		manoHandler.registerApiStartWith(ChassisConfig.ManoConfig.EXPORT_PATH.get(), metricHandler);
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.ALARM_PATH.get(), alarmHandler);
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.CUSTOM_ALARM.get(), alarmHandler);
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.HEALTH_CHECK_PATH.get(), new HealthCheckHandler(this::healthCheck));
		manoHandler.registerApiStartWith(ChassisConfig.ManoConfig.METRIC_PATH.get(), metricHandler);
		manoHandler.registerApiExact(ChassisConfig.ManoConfig.SHUTDOWN_PATH.get(), new HttpAPI(this::shutdown) {
			@Override
			public void handle(HttpServerContext context) {
				if (userHander != null) {
					userHander.handle(context);
					return;
				}
				context.send();
			}
		});
	}

	Runnable dispatchCallback(HttpServerContext serverCtx);

	default void manoFinalDispatch(HttpServerContext serverCtx) {
		ChassisMgrableMsg inMsg = serverCtx.getInMsg();
		if (inMsg instanceof HttpRequest) ((HttpRequest) inMsg).retain(); // retain to enqueue execute
		ManoUDAHandler manoUDAHandler = new ManoUDAHandler(serverCtx.getInPath(),
			String.valueOf(serverCtx.getInMsg().readableBytes()),
			serverCtx.getChannelString(),
			() -> {
				try {
					acceptedUDA.incrementAndGet();
					dispatchCallback(serverCtx).run();
				} finally {
					serverCtx.decompose();
				}
			});
		CompletableFuture.runAsync(manoUDAHandler, manoThreadPoolExecutor);
	}

	void healthCheck(HttpServerContext hctx);

	void metrics(HttpServerContext hctx);

	default List<NettyClient> getSubscribedManoClients() {
		return ConnectionManager.streamNettyClients(false).collect(Collectors.toList());
	}

	void shutdown(HttpServerContext hctx);

	void blueGreenUpdate(HttpServerContext hctx, BlueGreenUpdate updateInfo);
}



