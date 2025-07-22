package com.viettel.vocs.microchassis.serviceRegistry.codec;

import com.viettel.vocs.microchassis.connection.config.mesh.ClientMeshConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.common.IDfy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;

/**
 * @author tiennn18
 */
public class LBRClientInfo extends LBRPeer implements IDfy {
	private static final Logger logger = LogManager.getLogger(LBRClientInfo.class);
	// field for communicate over LBRPod msg
	public ClientMeshConfig.ClientDMirror dconfig = new ClientMeshConfig.ClientDMirror();

	public LBRClientInfo() {
	} // for encode decode

	public LBRClientInfo(String clientId, Endpoint endpoint) { // for client
		super(clientId, endpoint);
		logger.info("Create client with dest {}", endpoint);
	}

	public void write(Site<?> client) { // update info from site
		currentMap = client.countConnectedIps(); // for statefull relocate
		dconfig.read(client.getMesh());
		logger.info("Client {} update max {} min {} cluster {} dests {}", client.getId(), dconfig.maxConnection, dconfig.minConnection, dconfig.clusterConnection, targetMap);
	}

	public void copy(LBRClientInfo source) {
		dconfig.read(source.dconfig);
		targetMap = source.targetMap;
		id = source.id;
		endpoint = source.endpoint;
	}

	public String reporTargets() {
		return String.format("%s", targetMap.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")));
	}
}
