package com.viettel.autotest.microchassis.feature;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

/**
 * @author tiennn18
 */
public class ChassisConfigTest {
	@Test
	void loadConnection() {
		assert new File(ChassisConfig.ConnectionConfig.DEFAULT_CONNECTION_PATH.get()).getParentFile().exists();
	}
	@Test
	void readProps() {
		assert "hohohehe/connection.yml".equals(ChassisConfig.ConnectionConfig.DEFAULT_CONNECTION_PATH);
	}
	@Test
	void readEnv() {
		assert "/etc/connection.yml".equals(ChassisConfig.ConnectionConfig.DEFAULT_CONNECTION_PATH);
	}
	@Test
	void readDefault() {
		assert "../etc/connection.yml".equals(ChassisConfig.ConnectionConfig.DEFAULT_CONNECTION_PATH);
	}
	@Test
	void readListDef() throws Exception {
		System.out.println("HMMM="+ Arrays.toString(CommonConfig.JavaConfig.LIB_PATH.getArray()));
		assert Arrays.equals(new String[]{"../lib", "../lib-common"}, CommonConfig.JavaConfig.LIB_PATH.getArray());
		System.out.println("HMMM="+ Arrays.toString(ChassisConfig.HealthcheckConfig.HEALTHY_HTTP_CODE.getArray()));
		assert Arrays.equals(new int[]{200, 204}, ChassisConfig.HealthcheckConfig.HEALTHY_HTTP_CODE.getArrayInt());

		ConnectionManager.getInstance().loadYmlConfig();
		assert ConnectionManager.getInstance().getConfiguration() != null;
	}
}
