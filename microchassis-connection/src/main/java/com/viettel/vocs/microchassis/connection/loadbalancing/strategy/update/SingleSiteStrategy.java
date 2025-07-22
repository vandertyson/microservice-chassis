package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;

import java.util.List;

public class SingleSiteStrategy<Conf extends ClientConfiguration> extends UpdateStrategy<Conf> {
	public SingleSiteStrategy(Site<Conf> mainSite) {
		super(mainSite);
	}

	@Override
	public List<String> getAllSiteName() {
		return List.of(mainSiteHostName);
	}

	@Override
	public List<String> otherSiteNames(String thisSiteName) {
		return List.of(mainSiteHostName);
	}

	@Override
	public void refresh() {
		/**
		 * NO Weight map, so no need to be refreshed
		 */
	}

	@Override
	public UpdateSite selectSite(String key) {
		return anySite();
	}

	@Override
	public Site<Conf> anySite() {
		return destMap.get(mainSiteHostName);
	}

	@Override
	public String toString() {
		return "Status: {" + mainSiteHostName + ": 100% load}";
	}
}
