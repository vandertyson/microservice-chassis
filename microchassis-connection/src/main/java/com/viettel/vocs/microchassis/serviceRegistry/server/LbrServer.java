package com.viettel.vocs.microchassis.serviceRegistry.server;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.NameableThreadFactory;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.context.ChassisReplyContext;
import com.viettel.vocs.microchassis.connection.client.ChannelAttribute;
import com.viettel.vocs.microchassis.connection.dns.HostNameResolver;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.LBRAdapter;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.LBRConstants;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRMsg;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRServerInfo;
import com.viettel.vocs.microchassis.serviceRegistry.codec.context.LbrContext;
import com.viettel.vocs.microchassis.serviceRegistry.codec.handler.LbrHandler;
import com.viettel.vocs.microchassis.serviceRegistry.server.strategy.ServiceLBR;
import com.viettel.vocs.microchassis.tcp.server.TcpBasedServer;
import io.netty.channel.Channel;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;


/**
 * @author tiennn18
 * class for manage 1 service type orchestration
 * TODO specify for localhost and specific IP without DNS name
 */
public class LbrServer extends TcpBasedServer<LBRMsg, LbrServerConfiguration, LbrHandler, LbrServerChannelInitializer> {
	private static final Logger logger = LogManager.getLogger(LbrServer.class);
	public final Map<String /* dns service name*/ , Map<Integer, ServiceLBR>> dnsMap = new ConcurrentHashMap<>();
	@Override
	public LBRMsg createReq(byte[] data, String path, String msgId) {
		return null; // lbr client chi can copy tu representMsg nen ko can craeteReq
	}

	@Override
	public LBRMsg createReq(byte[] data, String path) {
		return null; // lbr client chi can copy tu representMsg nen ko can craeteReq
	}
	@Override
	protected LbrServerChannelInitializer newServerInitializer(LbrHandler handlers) {
		return new LbrServerChannelInitializer(this, handlers);
	}


	@Override
	public LBRAdapter getNegtor() {
		return defaultStlnLBRA;
	}

	@Override
	public LbrServer restart(LbrHandler handlers) throws Exception {
		return (LbrServer) super.restart(handlers);
	}

	@Override
	public LbrServer start(LbrHandler handlers) throws Exception {
		return (LbrServer) super.start(handlers);
	}

	public LbrServer(LbrServerConfiguration config) {
		super(config);
		new ThreadManager.ScheduledExecutor(1, new NameableThreadFactory(config.id)).scheduleWithFixedDelay(() -> {
			updateDNS();
			arrangeConnections(ChassisConfig.LBRConfig.assignMode.get());
		}, 0, LbrServerConfiguration.DNS_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
		eventHandler.addEventHandler(ContextEvent.EventType.CHANNEL_ACTIVE,(Consumer<ContextEvent>) event -> { // registra monitor connection alive
			logger.info("New pod connected {}", event.getEndpoint());
			// accept all connection, deny at handle
		});
		eventHandler.addEventHandler(ContextEvent.EventType.CHANNEL_INACTIVE, (Consumer<ContextEvent>)event -> { // registra monitor connection alive
			logger.info("Pod disconnected {}", event.getEndpoint());
			String ipToRemove = event.getCtxIp(); // khong dung duoc port cua Endpoint
			removeServerRefs(ipToRemove);
			removeClientRefs(ipToRemove);
			// else cannot do anything to delete -> TODO remove server if not polling after timeout = 3*basic polling timeout
		});
	}


	private void removeServerRefs(String removeIp) {
		dnsMap.values().forEach(
			portService -> portService.forEach(
				(port, service) -> service.removeServer(removeIp)));
	}

	private void removeClientRefs(String removeIp) {
		dnsMap.values().forEach(
			portServiceMap -> portServiceMap.values().forEach( // remove client ref cho tat ca cac obj con lai
				service -> service.clientMap.forEach((clientType, clientTypeMap) -> clientTypeMap.remove(removeIp))));
	}

	private void arrangeConnections(String mode) {
		// under capacity scan
//		Map<String /*quota*/, Integer> quotaMaxes = // TODO tim cach merge quota
		logger.info("Rearrange {} {}", mode, dnsMap);
		dnsMap.forEach((dns, portServiceMap) -> {
			logger.info("Port Service {}", portServiceMap);
			portServiceMap.forEach((port, s) -> {
				switch (mode) {
					case ChassisConst.LBRConst.ASSIGN_HASH:
						logger.info("Hash relocate {}:{} s={} c={}", dns, port,
							s.serverMap.values().stream().map(lbrs->lbrs.targetMap.toString()).collect(Collectors.joining(",")),
							s.clientMap);
						s.hashRelocate();
						break;
					case ChassisConst.LBRConst.ASSIGN_FILL:
					default:
						logger.info("Fill relocate");
						s.fillRelocate();
						break;
				}
			});
		});
	}

	private void updateDNS() {  // servers chung cho ca CNF
		dnsMap.forEach((dnsName, portService) -> {
			// service added by first registration on boot at ConnectionManager.negtor of pod
			Set<String> newResolved = HostNameResolver.doResolveDNS(dnsName);
			portService.values().forEach(serviceLBR -> serviceLBR.updateDNS(newResolved));
		});
	}

	private boolean registerHandle(ChassisReplyContext<LBRMsg,LBRMsg> lctx) {
		String dns = lctx.getInMsg().headers().getString(LBRMsg.DNS_NAME);
		if (dns == null) {
			// update without register before -> malicious: havent resolve this DNS, drop pod connect
			lctx.decompose(); // this call immediate CHANNEL_INACTIVE can do it impolitely
			return false;
		}
		if (LBRConstants.REGISTER_PATH.equals(lctx.getInPath())) {
			logger.info("Register for DNS {}", dns);
			dnsMap.putIfAbsent(dns, new ConcurrentHashMap<>());
			((LbrContext) lctx).ack("registered");
			return false;
		}
		return true;
	}

	private boolean deregisterHandle(ChassisReplyContext<LBRMsg,LBRMsg> lctx) {
		if (LBRConstants.DEREGISTER.equals(lctx.getInPath())) {
			// all server down, stop all client from this pod
			// TODO: code cai nay di
			// gom co politely deregister client
			// gom co politely deregister server
			// impolite disconnect se duoc xu ly tai CHANNEL_INACTIVE
			return false;
		}
		return true;
	}

	/**
	 * when update, new info is recorded, but might not be processed, it reply immediately with previous calculations
	 * next polling will result in latest schema
	 */
	private boolean updateHandle(ChassisReplyContext<LBRMsg,LBRMsg> lctx){
		Channel channel = lctx.getChannel();

		String dns = lctx.getInMsg().headers().getString(LBRMsg.DNS_NAME);
		if (LBRConstants.UPDATE_STATUS.equals(lctx.getInPath())) {
			LBRMsg reqCopy = lctx.getInMsg().replicate(); // sau khi reply thi req se bi release nen can copy de dua vao Center LBR ref, chi tao 1 obj va tat ca cac client deu ref vao day
			String pIp = ChannelAttribute.getRemoteIp(channel);
			logger.info("POD {} send servers {}", pIp, lctx.getInMsg().servers.values().stream().map(s->s.endpoint).collect(Collectors.toList()));
			logger.info("DNSMap: {}", dnsMap);
			reqCopy.servers.values().forEach(podSentLbrServer -> {
				// moi msg se den tu 1 pod la 1 msg type nen se duoc xu ly boi ServiceLBR do
				dnsMap.get(dns).computeIfAbsent(podSentLbrServer.endpoint.port, port -> {
					logger.info("Register for Map<DNS:{}, Map<dest:{}>>", dns, podSentLbrServer.endpoint);
					return new ServiceLBR(podSentLbrServer.endpoint);
				});
				ServiceLBR service = dnsMap.get(dns).get(podSentLbrServer.endpoint.port);
				LBRServerInfo centerLBRExistedServer = service.serverMap.get(pIp);
				boolean serverExistedInDNSCenterLBRresolve = false;
				if (centerLBRExistedServer != null) { // server ip may null bcuz DNS resolve late
					centerLBRExistedServer.lastPoll = TimeUtils.nowNano();
					// handle server ops // handle server diff
					serverExistedInDNSCenterLBRresolve = reqCopy.servers.values().stream()
						.map(s -> s.id)
						.collect(Collectors.toList())
						.contains(centerLBRExistedServer.id);
				}
				if (serverExistedInDNSCenterLBRresolve) {
					centerLBRExistedServer.lbrServerRead(podSentLbrServer);
				} else { // ton tai trong req nhung tren center khong co -> add
					service.addServer(podSentLbrServer); // podSentLbrServer sau khi release se khong mat
				}
				logger.info("Serrvice RReport {}", service.serverMap.values().stream().map(LBRServerInfo::status).collect(Collectors.joining(",")));
			});
			reqCopy.clients.forEach((id, lbrClient) -> {
				logger.info("Req client dest {}", lbrClient.endpoint);
				Map<Integer, ServiceLBR> registeredServiceForDNS = dnsMap.get(lbrClient.endpoint.serviceName);
				if(registeredServiceForDNS != null) { // maybe null neu dest server havent register for the first pod
					ServiceLBR targetService = registeredServiceForDNS.get(lbrClient.endpoint.port);
					if (targetService != null) { // co the null neu server port nay bat cham hon server port khac trong cung 1 pod
						targetService.clientMap.computeIfAbsent(reqCopy.get(LBRMsg.SERVICE_TYPE), t -> {
							targetService.clientTypeLogOffset.add(t);
							return new ConcurrentHashMap<>();
						});
						Map<String, Pair<String, LBRMsg>> clients = targetService.clientMap.get(reqCopy.get(LBRMsg.SERVICE_TYPE));
						clients.putIfAbsent(pIp, Pair.of(id, reqCopy
						)); // ref to pod that create connection to targetService
						// doi voi client id bien mat thi xu ly o Channel_INACTIVE removeClientRefs(ip)
						logger.info("Client map of Service {} is {}", lbrClient.endpoint, targetService.clientMap);
					}
				} // else do hoat dong update dinh ky nen khong can xu ly else, lan toi se co
			});
			extractForEndpointReply(lctx.getCoMsg(), dns, pIp);
			((LbrContext)lctx).ack("order");
		}
		return true;
	}


	private void extractForEndpointReply(LBRMsg replyMsg, String dns, String ip){
		replyMsg.servers.clear();
		logger.info("Overwrite ip {} with server {}", ip, dnsMap.get(dns).values().stream()
			.map(serviceLBR -> {
				logger.info("found server {} ip {}: {}", serviceLBR.serverMap, ip, serviceLBR.serverMap.values().stream().map(LBRServerInfo::status).collect(Collectors.joining(",")));
				return serviceLBR.serverMap.get(ip);
			}).collect(Collectors.toList()));
		dnsMap.get(dns).values().stream()
			.map(serviceLBR -> serviceLBR.serverMap.get(ip))
			.filter(Objects::nonNull)
			.forEach(server -> replyMsg.servers.put(server.id, server)); // tren pod quan ly serverMap theo id, tren Center LBR quan ly theo ip
		replyMsg.clients.clear();
		Optional<LBRMsg> clientOpt = dnsMap.get(dns).values().stream()
			.map(serviceLBR -> serviceLBR.clientMap.get(replyMsg.get(LBRMsg.SERVICE_TYPE)))
			.filter(Objects::nonNull)
			.map(serviceClientMap -> serviceClientMap.get(ip))
			.filter(Objects::nonNull)
			.map(Pair::getRight)
			.findFirst();
		clientOpt.ifPresent(lbrPod -> replyMsg.clients.putAll(lbrPod.clients));
	}

	public void removeTimeoutPods() { // call on too long not polling
		dnsMap.values().forEach(portService ->
			portService.values().forEach(serivce -> { // server identify by it selft, client identified by target server
				serivce.serverMap.forEach((ip, pod) -> {
					if (pod.lastPoll + 3L * TimeUnit.MILLISECONDS.toNanos(ChassisConfig.LBRConfig.POLLING_INTERVAL_MS.get()) < TimeUtils.nowNano()) serivce.removeServer(ip);
				});
			}));
	}

	private final Semaphore runningSemaphore = new Semaphore(1);
	private final ThreadManager.ScheduledExecutor worker = new ThreadManager.ScheduledExecutor(1, new NameableThreadFactory("lbrworker_" + config.id));

	public void startCycleJob() {
		logger.info("Start removeTimeoutPods job");
		worker.scheduleWithFixedDelay(() -> {
			try {
				runningSemaphore.acquire(); // Acquire a permit, blocking if the concurrency limit is reached
				removeTimeoutPods();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				runningSemaphore.release(); // Release the permit
			}
		}, 0, config.broadcastIntervalMs, TimeUnit.MILLISECONDS);
	}
	public void listen() {
		try {
			start(new LbrHandler(config, new AtomicInteger(0), getNegtor()) {
				@Override
				public void handle(LbrContext lctx) {
					try {
						if (lctx != null) {
							lctx.getInMsg().setState("processing");
							if (!(registerHandle(lctx)
								&& deregisterHandle(lctx)
								&& updateHandle(lctx)
							)) return;
						}
					} catch (Exception e) {
						logger.error(e, e);
					}
				}
			});
			while (ch != null) TimeUtils.waitSafeMili(1);
			logger.info("Closing {} server at {}", config.getProtocolName(), config.port);
		} catch (Throwable ex) {
			logger.error(ex, ex);
		}
		startCycleJob();
	}

	@Override
	protected LBRMsg buildStopMsg() {
		return LBRMsg.serverMakeStop(bytebufAllocator);
	}
}
