package com.viettel.vocs.microchassis.connection.config.mesh;

import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRServerInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ServerMeshConfig<Conf extends ServerConfiguration> extends MeshConfig {
	private static final Logger logger = LogManager.getLogger(ServerMeshConfig.class);
	private Conf config;
	Set<String> thisServerProps = ChassisConfig.MeshConfig.readAllMeshConfigs(id, ChassisConfig.MeshConfig.PeerRole.server);

	public int maxHost = maxConnection;
	public Map<String /* feature */, Map<String/* targetObject */ , Integer>> quotaMap = new ConcurrentHashMap<>();

	public static class ServerDMirror extends MeshConfig.Mirror {
		public int maxHost;
		public Map<String, Map<String, Integer>> quotaMap = new ConcurrentHashMap<>();

		public void read(ServerMeshConfig<?> mesh) {
			maxConnection = mesh.maxConnection;
			minConnection = mesh.minConnection;
			maxHost = mesh.maxHost;
			quotaMap = mesh.quotaMap;
		}

		public void read(ServerDMirror dconfig) {
			maxConnection = dconfig.maxConnection;
			minConnection = dconfig.minConnection;
			maxHost = dconfig.maxHost;
			quotaMap = dconfig.quotaMap;
		}
	}

	public void read(LBRServerInfo server) { // from server order, overwrite all
		maxConnection = server.dconfig.maxConnection;
		minConnection = server.dconfig.minConnection;
		maxHost = server.dconfig.maxHost;
		quotaMap = server.dconfig.quotaMap;
		logger.info("Server {} update max {} min {} host {}", getId(), server.dconfig.maxConnection, server.dconfig.minConnection, server.dconfig.maxHost);
	}

	protected Map<String, String> readServerMeshConfigs(String feature, String defaultVal) { // read non-default props
		return thisServerProps.stream()
			.map(prop -> {
				try {
					int ididx = prop.indexOf('.', prop.indexOf('.') + 1); // second dot
					int idxPreQuotaName = prop.indexOf('.', ididx + 1); // third dot
					int idxPostQuotaName = prop.indexOf('.', idxPreQuotaName + 1); // 4th dot
					String quotaName = prop.substring(idxPreQuotaName + 1, idxPostQuotaName);
					logger.info("Quota name {}: {}", quotaName, ChassisConfig.MeshConfig.readSpecificMeshConfig(ChassisConfig.MeshConfig.PeerRole.server, id, quotaName, feature, defaultVal));
					return quotaName;
				} catch (Exception e) { // may have short prop without 4th dot (only 4 strings)
					return null;
				}
			})
			.filter(Objects::nonNull)
			.collect(Collectors.toMap(quotaName -> quotaName,
				quotaName -> ChassisConfig.MeshConfig.readSpecificMeshConfig(ChassisConfig.MeshConfig.PeerRole.server, id, quotaName, feature, defaultVal)
				// mesh.server.tcp-server.chp1.connection.max=2
				// mesh.server.tcp-server.connection.max=2
			));
	}


	private void buildDefaultVals() {
		maxConnection = Integer.parseInt(ChassisConfig.MeshConfig.readDefaultMeshConfigs(ChassisConfig.MeshConfig.PeerRole.server, thisServerProps, id, CONN_MAX, String.valueOf(config.maxConnection)));
		maxHost = Integer.parseInt(ChassisConfig.MeshConfig.readDefaultMeshConfigs(ChassisConfig.MeshConfig.PeerRole.server, thisServerProps, id, HOST_MAX, String.valueOf(config.maxHost)));
		minConnection = Integer.min(maxConnection, // max bias
			Integer.parseInt(ChassisConfig.MeshConfig.readDefaultMeshConfigs(ChassisConfig.MeshConfig.PeerRole.server, thisServerProps, id, CONN_MIN, String.valueOf(config.minConnection)))
		);
		mode = Mode.fromString(ChassisConfig.MeshConfig.readDefaultMeshConfigs(ChassisConfig.MeshConfig.PeerRole.server, thisServerProps, id, "type", config.connectionLimitMode)); // "mesh.server..type"
	}


	protected void buildQuotaMap() {
		// to overwrite: set mesh.server.<clientId>.connection.min/max=0++
		quotaMap.putIfAbsent(CONN_MAX, readServerMeshConfigs(CONN_MAX, String.valueOf(config.maxConnection)).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> Integer.parseInt(e.getValue()))));
		quotaMap.putIfAbsent(CONN_MIN, readServerMeshConfigs(CONN_MIN, String.valueOf(config.minConnection)).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> Integer.parseInt(e.getValue()))));
	}

	public int getQuota(String quotaName, String feature) { // quotaName ~ clientId
		Map<String, Integer> featureVals = quotaMap.get(feature);
		if (mode != Mode.FULL_MESH && featureVals.containsKey(quotaName)) {
			return featureVals.get(quotaName);
		}
		return 0; // unlimited
	}

	public ServerMeshConfig(Conf config) {
		super(config.id);
		this.config = config;
		buildDefaultVals();
		logger.info("Start SERVERD with config mode:{} minCon:{} maxCon:{} maxHost:{}", mode, minConnection, maxConnection, maxHost);
	}
}
