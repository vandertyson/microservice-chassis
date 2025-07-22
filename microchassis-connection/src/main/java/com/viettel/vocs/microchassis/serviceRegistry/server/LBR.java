package com.viettel.vocs.microchassis.serviceRegistry.server;

/**
 * @author tiennn18
 */

import com.viettel.vocs.common.file.YamlUtils;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * join negotiate cluster, identify service type by string identifier
 * use TCP for negotiate, every HTTP child class must overlay base TCP
 */
public class LBR {
	private static final Logger logger = LogManager.getLogger("com.viettel.vocs.microchassis.Negotiator");
	protected final Map<String /*DNS name */, LbrServer> cnfMap = new ConcurrentHashMap<>();
	private ClusterLBConfiguration configuration;

	private static volatile LBR instance = null;

	public static synchronized LBR getInstance() {
		if (instance == null) {
			try {
				instance = new LBR();
			} catch (Exception ex){
				logger.error(ex, ex);
			}
		}
		return instance;
	}
	public void loadYmlConfig(String ymlPath) throws Exception {
		loadLbrServerConfig(ymlPath);
		configuration.namespace.stream()
			.collect(Collectors.toMap(conf -> conf.id, conf->conf, (old, duplicate)->old)) // handle duplicate id
			.values().stream()
			.map(LbrServer::new)
			.forEach(cnfServer -> cnfMap.put(cnfServer.getId(), cnfServer));
	}
	public void loadLbrServerConfig(String ymlPath) throws Exception {
		configuration = YamlUtils.objectFromYaml(ClusterLBConfiguration.class, ymlPath);
	}

	private LBR() throws Exception{
		loadYmlConfig(ChassisConfig.LBRConfig.DEFAULT_LBR_PATH.get());
		logger.info("Loaded yml");
	}
	ThreadManager.PoolExecutor serverListeners = ThreadManager.newFixedThreadPool(cnfMap.size());
	public void start(){
		cnfMap.values().forEach(v-> serverListeners.submit(v::listen));
	}

	public static void main(String[] args){
		LBR.getInstance().start();
	}
}
