package com.viettel.autotest.microchassis.connection.config;

import com.viettel.vocs.common.CommonConfig;

public interface GeneratorConfig {
	int getGenerateThreads();

	int getFakeTPS();

	int getGenerateThreadsPerfTPS4kB();

	int getFakePayloadSize();
	int getNWindowToRotate();

	default int evaluateGenerateThreads() { // moi thread gen dc 5ktps
		return getGenerateThreads() != 0 ? getGenerateThreads() : getFakeTPS() > 0
			? Integer.min(
			CommonConfig.JavaConfig.N_CORE.get(),
			(int) Math.ceil(getFakeTPS() / ((double) getGenerateThreadsPerfTPS4kB() * 4096 / getFakePayloadSize()))
		) : CommonConfig.JavaConfig.N_CORE.get();
	}
}
