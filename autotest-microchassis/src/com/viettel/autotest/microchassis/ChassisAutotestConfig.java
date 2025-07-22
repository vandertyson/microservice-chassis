package com.viettel.autotest.microchassis;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.config.value.IntegerConfig;
import com.viettel.vocs.common.config.value.StringConfig;

import java.io.File;

/**
 * @author tiennn18
 */
public interface ChassisAutotestConfig {
	IntegerConfig reqIDstart = new IntegerConfig(null, "reqRange", 100_000_000L);
	StringConfig msisdnPattern = new StringConfig(null, "msisdnPattern", "{valuemsisdn}");

	StringConfig payloadFile = new StringConfig(null, "payloadFile", new File(CommonConfig.JavaConfig.CONFIG_FOLDER.get(), "payload.json").toString());
	int thirdPartyPort = CommonConfig.TestConfig.thirdPartyPort.getInt();
}
