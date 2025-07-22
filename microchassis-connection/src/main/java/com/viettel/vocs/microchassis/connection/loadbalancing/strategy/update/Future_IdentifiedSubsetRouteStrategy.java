package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;

/**
 * @author tiennn18
 */
public abstract class Future_IdentifiedSubsetRouteStrategy<Identifier,  Z extends ClientConfiguration> extends UpdateStrategy<Z> {
	/**
	 * this strategy remember connection
	 */

	protected Future_IdentifiedSubsetRouteStrategy(Site<Z> mainSite) {
		super(mainSite);
	}

}
