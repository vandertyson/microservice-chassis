/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.dns;

import com.viettel.vocs.microchassis.base.ChassisConst;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;


/**
 * @author vttek
 */
public interface HostNameResolver {
	static final Logger logger = LogManager.getLogger(HostNameResolver.class);

	static boolean isValidIPV4(final String s) {
		return ChassisConst.IPV4_PATTERN.matcher(s).matches();
	}

	static final Map<String, Set<String>> mapDnsIp = new ConcurrentHashMap<>();

	static void addIpForDns(String dns, Collection<String> ips) {
		if (ips != null) mapDnsIp.computeIfAbsent(dns, s -> new CopyOnWriteArraySet<>()).addAll(ips);
	}

	static Set<String> doResolveDNS(String hostName) {
		Set<String> ret = new CopyOnWriteArraySet<>();
		if (mapDnsIp.containsKey(hostName)) { // if hostname is localhost or ip, add it first to ret, then merge with above to return
			Set<String> mapResolved = mapDnsIp.get(hostName);
			if (logger.isDebugEnabled()) logger.debug("[Resolved by mapDnsIp]{name={}, count={}, ips={}}", hostName, mapResolved.size(), mapResolved);
			ret.addAll(mapResolved);
		}
		if ("localhost".equals(hostName) || isValidIPV4(hostName)) {
			ret.add(hostName);
			return ret;
		}
		try {
			InetAddress[] inetResolved = InetAddress.getAllByName(hostName);
			if (logger.isDebugEnabled()) logger.debug("[Resolved by java InetAddress]{name={}, count={}}", hostName, inetResolved.length);
			ret.addAll(Arrays.stream(inetResolved).map(InetAddress::getHostAddress).collect(Collectors.toList()));
		} catch (Exception ex) {
			if(!ret.isEmpty()) {
				if(logger.isDebugEnabled()) logger.info("Resolve failed but merged DNS, caused by ex: {}", ex.getMessage());
			} else { // empty
				logger.error(ex, ex);
				return null; // for trigger RESOLVE_DNS_FAIL
			}
		}
		return ret;
	}

	static void removeIpForDns(String dns, Collection<String> ips) {
		Set<String> strings = mapDnsIp.get(dns);
		if (ips != null && strings != null) ips.forEach(strings::remove);
	}
}
