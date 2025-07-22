package com.viettel.vocs.microchassis.connection.loadbalancing.strategy;

import java.util.Set;

public interface ServiceMonitorable {
	String getHostName(); // return main site hostname
	// getter dynamic props
	void seedCreateConnJobs(int connNeedBalance);
	void updateDNS(Set<String> resolvedIps);
	Set<String> resolveDNS(); // trigger NettyClient self (Site)reresolveDNS for all updateSite(s)
}
