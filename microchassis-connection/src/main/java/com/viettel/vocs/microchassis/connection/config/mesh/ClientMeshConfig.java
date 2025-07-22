package com.viettel.vocs.microchassis.connection.config.mesh;

import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRClientInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

/**
 * @author tiennn18
 * config and executor for mesh deduction
 */
public class ClientMeshConfig<Conf extends ClientConfiguration> extends MeshConfig {
	private static final Logger logger = LogManager.getLogger(ClientMeshConfig.class);
	public int clusterConnection = maxConnection;
	public int minHost = 1; // for HA
	private final Conf config;
	Set<String> thisClientProps = ChassisConfig.MeshConfig.readAllMeshConfigs(id, ChassisConfig.MeshConfig.PeerRole.client);

	public static class ClientDMirror extends MeshConfig.Mirror {
		public int clusterConnection;
		public int minHost;
		public void read(ClientMeshConfig<?> clientMeshConfig){
			maxConnection = clientMeshConfig.maxConnection;
			minConnection = clientMeshConfig.minConnection;
			minHost = clientMeshConfig.minHost;
			clusterConnection = clientMeshConfig.clusterConnection;
		}
		public void read(ClientDMirror dconfig){
			maxConnection = dconfig.maxConnection;
			minConnection = dconfig.minConnection;
			minHost = dconfig.minHost;
			clusterConnection = dconfig.clusterConnection;
		}
	}

	public void read(LBRClientInfo client) { // from LBR server order, overwrite all
		destinations = client.targetMap;
		clusterConnection = client.dconfig.clusterConnection;
		maxConnection = client.dconfig.maxConnection;
		minHost = client.dconfig.minHost;
		minConnection = client.dconfig.minConnection;
		logger.info("Client {} update max {} min {} cluster {} dests {}", getId(), client.dconfig.maxConnection, client.dconfig.minConnection, client.dconfig.clusterConnection, destinations);
	}


	private void buildDefaultVals(){
		// to overwrite: set mesh.client.<client.id>.connection.min/max=0++
		maxConnection = Integer.parseInt(ChassisConfig.MeshConfig.readDefaultMeshConfigs(ChassisConfig.MeshConfig.PeerRole.client, thisClientProps, id, CONN_MAX, String.valueOf(config.maxConnection)));
		minHost = Integer.parseInt(ChassisConfig.MeshConfig.readDefaultMeshConfigs(ChassisConfig.MeshConfig.PeerRole.client, thisClientProps, id, HOST_MIN, String.valueOf(config.minHost)));
		minConnection = Integer.min(maxConnection, // max bias if max less than min
			Integer.parseInt(ChassisConfig.MeshConfig.readDefaultMeshConfigs(ChassisConfig.MeshConfig.PeerRole.client, thisClientProps, id, CONN_MIN, String.valueOf(config.minConnection)))
		);  // in config guaranteed >= 0
		mode = Mode.fromString(ChassisConfig.MeshConfig.readDefaultMeshConfigs(ChassisConfig.MeshConfig.PeerRole.client, thisClientProps, id, "type", config.connectionLimitMode)); // "mesh.client..type"
		clusterConnection = Integer.parseInt(ChassisConfig.MeshConfig.readDefaultMeshConfigs(ChassisConfig.MeshConfig.PeerRole.client, thisClientProps, id, CLUSTER_SIZE, "1")); // havent used this param, just relocate :))
	}

	public ClientMeshConfig(Conf config) {
		super(config.id);
		this.config = config;
		buildDefaultVals();
		logger.info("Start CLIENTD {} with config mode:{} minCon:{} cluster:{} maxCon:{}", id, mode, minConnection, clusterConnection, maxConnection);
	}
}
