/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.dns;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.ServiceMonitorable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author vttek
 */
public interface ServiceMonitor {

	Logger logger = LogManager.getLogger(ServiceMonitor.class);
	List<ServiceMonitorable> servicesToDiscovery = new CopyOnWriteArrayList<>();
	AtomicLong lastServiceCheck = new AtomicLong();
	Map<String, List<ServiceMonitorable>> mapTobeResolveNames = new ConcurrentHashMap<>();

	static void setMonitorServices(ServiceMonitorable service) {
		if (servicesToDiscovery.contains(service)) {
			return;
		}
		servicesToDiscovery.add(service);
		String hostName = service.getHostName();
		if (hostName != null) {
			if (HostNameResolver.isValidIPV4(hostName)) {
				logger.error("Invalid dns name. Will not monitor {}", hostName);
			} else {
				mapTobeResolveNames.computeIfAbsent(hostName, f -> new CopyOnWriteArrayList<>())
					.add(service);
			}
		}
		logger.info("[Register service discovery]{service={},size={}, hostName=[{}]}",
			service, servicesToDiscovery.size(), servicesToDiscovery.stream().map(ServiceMonitorable::getHostName).collect(Collectors.joining(", ")));
	}

	static void unsetMonitorServices(ServiceMonitorable service) {
		boolean remove = servicesToDiscovery.remove(service);
		String hostName = service.getHostName();
		if (hostName != null) {
			if (HostNameResolver.isValidIPV4(hostName)) {
				logger.error("Invalid dns name. Skip remove monitor {}", hostName);
			} else {
				List<ServiceMonitorable> serviceTemplatesShareHostName = mapTobeResolveNames.get(hostName);
				serviceTemplatesShareHostName.remove(service);
			}
		}
		logger.info("[Unregister service discovery]{service={},success={}, remain={}}", service, remove, servicesToDiscovery.size());
	}

	static long serviceDiscoveryDurationNanos(){
		return TimeUnit.MILLISECONDS.toNanos(ChassisConfig.RouteConfig.ServiceDiscoveryConfig.serviceDiscoveryIntervalMs.get());
	}

	static void stop() {
		servicesToDiscovery.clear();
	}
	Runnable job = ()-> {
		try {
			// trigger once each Interval, iff last job done, else skip this interval
			long trigger = TimeUtils.nowNano();
			if (TimeUtils.sharedAEX.anchorExecuteUpSide(()->{
				try {
					// null safe, da check khi add mapResolve
					mapTobeResolveNames.forEach((hostName, value) -> {
						if (hostName.isEmpty()) return;
						Optional<ServiceMonitorable> optServiceDiscoverable = value.stream().findFirst();
						if (optServiceDiscoverable.isPresent()) {
							Set<String> resolvedIps = optServiceDiscoverable.get().resolveDNS(); // refresh each monitor
							if (logger.isDebugEnabled())
								logger.debug("[Service discovery started]{name={} to {}}", hostName, resolvedIps);
							if (resolvedIps != null) // resolve once, apply all relavent
								value.forEach(sameHostNameService -> {
									sameHostNameService.updateDNS(resolvedIps); // apply new resolved to Site
									sameHostNameService.seedCreateConnJobs(0); // need call each monitor to create new conns
								});
							if (logger.isDebugEnabled())
								logger.debug("[Service discovery finished]{name={}}", hostName);
						}
					});
				} catch (Throwable e){
					logger.error("[Service discovery failed]{exception="+e.getMessage()+"}", e);
				}
			}, serviceDiscoveryDurationNanos())) {
				logger.debug("Triggered Resolver");
				lastServiceCheck.set(trigger); // only for report
			}
			if (logger.isDebugEnabled())
				logger.info("Service monitor status. countServices={}, lastCheckService={}}",
					servicesToDiscovery.size(), TimeUtils.miliToString(lastServiceCheck.get() / 1_000_000L));
		} catch (Exception e) {
			logger.error(e, e);
		}
	};

	static String report() {
		return String.format( "ServiceMonitor{services=%s, lastCheck=%d, mapResolveName=%s}",servicesToDiscovery, lastServiceCheck.get(), mapTobeResolveNames);
	}
}
