package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;


/**
 * @author tiennn18
 */
public abstract class Future_CanaryStrategy<Identifier,  Z extends ClientConfiguration> extends Future_IdentifiedSubsetRouteStrategy<Identifier, Z> {
	protected Future_CanaryStrategy(Site<Z> mainSite) {
		super(mainSite);
	}
	/**
	 * IBM: start by updating a single node or a subset of nodes.
	 * 	That way, you can test the update on a small subset of your system before you update every node.
	 */

}
