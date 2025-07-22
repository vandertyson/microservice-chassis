package com.viettel.vocs.microchassis.connection.loadbalancing.negotiator;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.NameableThreadFactory;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.serviceRegistry.client.LBRClientConfiguration;
import com.viettel.vocs.microchassis.serviceRegistry.client.LbrClient;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRClientInfo;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRMsg;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRServerInfo;
import com.viettel.vocs.microchassis.serviceRegistry.codec.handler.LbrHandler;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author tiennn18
 * this is client connect to ServiceLBRegistry
 * update are record local, then sync every 1s
 * accept server push, accpet urgent sync
 */
public class CenterLBR extends LBRAdapter {

	private final LbrClient client;  // only CenterLBR has tcp client to communicate with ServiceLBRegistry server


	private synchronized void sync() {
		if(client.sessionConnected.get()) {
			try {
				representMsg.reuse(LBRConstants.UPDATE_STATUS);
				representMsg.setState("sync");
				LBRMsg sendMsg = representMsg.replicate();
				client.send(sendMsg);
			} catch (Exception ex) {
				logger.error(ex, ex);
			}
		} else {
			register();
		}
	}


	@Override
	public void registerServer(NettyServer server) { // , boolean isHttp) { // trigger on Server constructor
		Endpoint e = server.getEndpoint();
		if (e != null) {
			logger.info("Register server {}", e);
			LBRServerInfo lbrServerInfo = new LBRServerInfo(server.getId(), e);
			lbrServerInfo.write(server);
			representMsg.servers.put(server.getId(), lbrServerInfo);
			logger.info("Added {} server to representPod", server.getId());
			sync();
		}
	}

	@Override
	public void registerClient(Site<?> client) { // trigger on Site constructor
		Endpoint e = client.getEndpoint();
		if (e != null) {
			logger.info("Register for client {}", e);
			LBRClientInfo lbrClientInfo = new LBRClientInfo(client.getId(), e);
			lbrClientInfo.write(client);
			representMsg.clients.put(client.getId(), lbrClientInfo);
			logger.info("Added {} client to representPod", client.getId());
			sync();
		}
	}


	@Override
	public void deregisterServer(String id) {
		representMsg.servers.remove(id);
		logger.info("Removed {} server to representPod", id);
		sync();
	}

	@Override
	public void deregisterClient(String id) {
		representMsg.clients.remove(id);
		logger.info("Removed {} client to representPod", id);
		sync();
	}


	@Override
	public void updateClients(Map<String, LBRClientInfo> clients) {
		// TODO define this
	}

	@Override
	public void updateClient(Site<?> site) {
		getClient(site.getId()).write(site);
	}

	@Override
	public void updateServers(Map<String, LBRServerInfo> servers) {
		// TODO define this
	}

	@Override
	public void updateServer(NettyServer server) {
		getServer(server.getId()).write(server);
	}

	//	@Override
	public LBRClientInfo getClient(String id) {
		return representMsg.clients.get(id);
	}

	//	@Override
	public LBRServerInfo getServer(String id) {
		return representMsg.servers.get(id);
	}


	private void enable() {
		peerDropCounter.set(0);
	}

	private final AtomicInteger peerDropCounter = new AtomicInteger(0); // when < config.peerDropToAutonomous -> enable

	@Override
	public CenterLBR waitInit(){
		// TODO convert this to first time trigger, if failed fall back into ASK and run monitor job to retry granting to CENTERLIZED later
		// 	need to add compatible to work between few server receive order from CenterLBR and few in ASK mode trying to retry
		while (!client.sessionConnected.get() || !client.routeStrategyConnected()){
			logger.info("Wait for Center LBR {} connection. {}s, has {}/{} connections, {} {}", client.getEndpoint(), ChassisConfig.LBRConfig.POLLING_INTERVAL_MS, client.countConnected(), client.countConnections(), client.sessionConnected.get(), client.isSendable());
			TimeUtils.waitSafeMili(ChassisConfig.LBRConfig.POLLING_INTERVAL_MS.get());
		}
		return this;
	}

	public CenterLBR(LBRClientConfiguration config) {
		super(config);
		enable();
		client = new LbrClient(this.config);
		representMsg = new LBRMsg(client.getBytebufAllocator()); // do client va server register vao day 1 lan duy nhat nen phai la final va copy moi khi gui sync
		logger.info("Created CenterLBR: Represent at start headers = {}", representMsg.headers());
		try {
			// TODO define send timeout
			client.start(new LbrHandler(config, peerDropCounter, this));
		} catch (Exception ex) {
			logger.error(ex, ex);
		}
		register();
		logger.info("Represent header before fisrt sync = {}", representMsg.headers());
		new ThreadManager.ScheduledExecutor(1, new NameableThreadFactory(CommonConfig.InstanceInfo.INSTANCE_ID.get()))
			.scheduleWithFixedDelay(this::sync, 0, ChassisConfig.LBRConfig.POLLING_INTERVAL_MS.get(), TimeUnit.MILLISECONDS);
	}

	private void register() {
		waitInit();
		while (true) {
			try { // register for CenterLBR resolveDNS before calculates
				representMsg.reuse(LBRConstants.REGISTER_PATH);
				LBRMsg res = client.sendSync(representMsg.replicate());
				logger.info("Register: state={} header={}", res.getState(), res.headers());
				if (applyUpdate(res)) {
					logger.info("Registered with LBR server, start sync");
					break;
				}
				TimeUtils.waitSafeMili(ChassisConfig.LBRConfig.POLLING_INTERVAL_MS.get());
			} catch (Exception ex) {
				logger.error(ex, ex);
			}
		}
	}
}
