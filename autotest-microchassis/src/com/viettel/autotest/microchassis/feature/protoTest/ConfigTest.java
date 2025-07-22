package com.viettel.autotest.microchassis.feature.protoTest;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import org.junit.jupiter.api.Test;

/**
 * @author tiennn18
 */
public class ConfigTest extends TcpEchoTest{
	public static ConnectionManager manager = ConnectionManager.getInstance();
	static {
		CommonConfig.JavaConfig.CONFIG_FOLDER.setDefault("../etc/configTest");
		try {
			manager.loadYmlConfig();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		assert manager.getConfiguration() != null;
	}

	@Test
	void reloadStrategyTest(){
	}

	@Test
	void reloadLBRTest() {
	}

	@Test
	void affinityCPUTest(){

	}
}
