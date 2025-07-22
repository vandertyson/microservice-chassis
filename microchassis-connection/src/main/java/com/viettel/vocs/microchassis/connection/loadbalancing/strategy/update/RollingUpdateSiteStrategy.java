package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;

/**
 * @author tiennn18
 * IBM: Rolling deployments update large environments a few nodes at a time.
 * The setup for rolling deployments can be similar to blue-green deployments
 * weight map for rolling instance should be fixed in equality to rolling instance performance to not overload rolling part
 */
public class RollingUpdateSiteStrategy<Conf extends ClientConfiguration> extends SingleSiteStrategy<Conf> {
	public RollingUpdateSiteStrategy(Site<Conf> mainSite) {
		super(mainSite);
	}
}
