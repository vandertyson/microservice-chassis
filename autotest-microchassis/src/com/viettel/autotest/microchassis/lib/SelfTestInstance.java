package com.viettel.autotest.microchassis.lib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tiennn18
 */
public abstract class SelfTestInstance {
	protected static final Logger logger = LogManager.getLogger(SelfTestInstance.class);

	public static AtomicBoolean isTested = new AtomicBoolean(false);
	public static AtomicReference<byte[]> content = new AtomicReference<>();

}

