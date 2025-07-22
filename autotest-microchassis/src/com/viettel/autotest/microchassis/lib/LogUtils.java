package com.viettel.autotest.microchassis.lib;

import org.apache.logging.log4j.core.config.Configurator;

/**
 * @author tiennn18
 */
public interface LogUtils {
	private static void debugNetty() {
		Configurator.setLevel("com.viettel", org.apache.logging.log4j.Level.DEBUG);
		Configurator.setLevel("io.netty", org.apache.logging.log4j.Level.DEBUG);
	}
}
