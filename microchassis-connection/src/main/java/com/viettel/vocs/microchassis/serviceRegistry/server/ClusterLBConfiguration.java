package com.viettel.vocs.microchassis.serviceRegistry.server;

import com.viettel.vocs.microchassis.connection.config.ConnectionConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tiennn18
 */
public class ClusterLBConfiguration extends ConnectionConfiguration {
	;
	public List<LbrServerConfiguration> namespace = new ArrayList<>();
}
