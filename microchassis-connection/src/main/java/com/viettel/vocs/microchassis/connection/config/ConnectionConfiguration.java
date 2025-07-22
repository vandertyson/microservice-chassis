/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.config;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.common.config.loader.ConfigLoader;
import com.viettel.vocs.microchassis.serviceRegistry.client.LBRClientConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author vttek
 */
public class ConnectionConfiguration extends ConfigLoader<ConnectionConfiguration> {
	public boolean warmup = ChassisConfig.PerformanceConfig.WarmupConfig.WARMUP_DEFAULT.get();
	public List<LBRClientConfiguration> lbrs = new ArrayList<>();
	public List<ClientConfiguration> clients = new ArrayList<>();
	public List<ServerConfiguration> servers = new ArrayList<>();
	public boolean metricEnable = true;
	// const but can be overwritten: each var come with static value for other if need ref by static, and instance value for most of the cases
	public String dnsName = CommonConfig.InstanceInfo.AUTHEN_SERVICE_NAME.check();
	public String manoUpdateSuffix = ChassisConfig.ManoConfig.BLUE_GREEN_SUFFIX.get();
	@Override
	public boolean equals(Object obj) {
		if (obj == null || this.getClass() != obj.getClass()) {
			return false;
		} else if (this == obj) {
			return true;
		} else {
			return !diff((ConnectionConfiguration) obj);
		}
	}
	public boolean diff(ConnectionConfiguration obj) {
		return obj == null
			|| obj.warmup != warmup
			|| !Objects.equals(lbrs, obj.lbrs)
			|| !Objects.equals(clients, obj.clients)
			|| !Objects.equals(servers, obj.servers)
			|| !Objects.equals(dnsName, obj.dnsName)
			|| !Objects.equals(manoUpdateSuffix, obj.manoUpdateSuffix);
	}
}
