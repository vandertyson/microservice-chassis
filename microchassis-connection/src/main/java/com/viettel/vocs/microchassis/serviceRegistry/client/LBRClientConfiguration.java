package com.viettel.vocs.microchassis.serviceRegistry.client;

import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;

import java.util.Objects;

import static com.viettel.vocs.microchassis.base.ChassisConst.STANDALONE_LBR_ID;

public class LBRClientConfiguration extends ClientConfiguration {
	public static final String STANDALONE_LBR_CLIENT = "standalone";
	public static final String CENTER_LBR_CLIENT = "center";
	public String type = STANDALONE_LBR_CLIENT;

//	public LBRClientConfiguration(Properties properites) {
//		super(properites);
//	}

	public LBRClientConfiguration() {
		super("localhost", 0, STANDALONE_LBR_ID, STANDALONE_LBR_ID);
	}

	@Override
	public boolean diff(PeerConfig obj) {
		if (!(obj instanceof LBRClientConfiguration)) return true;
		LBRClientConfiguration o = (LBRClientConfiguration) obj;
		return !Objects.equals(o.type, type);
	}
}
