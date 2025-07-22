package com.viettel.vocs.microchassis.connection.loadbalancing.negotiator;

/**
 * @author tiennn18
 */

import com.viettel.vocs.microchassis.serviceRegistry.client.LBRClientConfiguration;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRMsg;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * join with all other LB that has same service type
 * LBR = Load balancer and Registry https://www.nginx.com/blog/service-discovery-in-a-microservices-architecture/
 * each program has only 1 instance of this. All client create that being monitored by LBRegistry
 */
public abstract class LBRAdapter implements LBNegotiator {
	protected static final Logger logger = LogManager.getLogger(LBRAdapter.class);
//	public static String POD_IP = CommonConfig.InstanceInfo.HOST_IP.get();
//	public static String SERVICE_TYPE_VALUE = CommonConfig.InstanceInfo.VDU_NAME.get();
//	public static String CNF_NAME = CommonConfig.InstanceInfo.VNF_INSTANCE_NAME.get();
	public final LBRClientConfiguration config;

	public abstract LBRAdapter waitInit();
	public LBRMsg representMsg;
	public boolean applyUpdate(LBRMsg res) {
		switch (res.getState()) {
			case "update": // from server
				logger.info("Synced {}, applying {}", res.getUrl(), res.states());
				representMsg.copyAdvanceFrom(res);
				logger.info("Represent after copy={}", representMsg.states());
				return true;
			case "registered":
				return LBRConstants.REGISTER_PATH.equals(res.getUrl());
			default:
				logger.info("Call url {} LBR server failed: {}", res.getUrl(), res.getState());
		}
		return false;
//		updateTcpClients(req.tcpClients);
//		updateHttpClients(req.httpClients);
//		updateClients(req.clients);
//		updateServers(res.servers);
//		updateTcpServers(req.servers);
//		updateHttpServers(req.httpServers);
//		return true;
	}
	protected LBRAdapter(LBRClientConfiguration config) {
		this.config = config;
	}
}