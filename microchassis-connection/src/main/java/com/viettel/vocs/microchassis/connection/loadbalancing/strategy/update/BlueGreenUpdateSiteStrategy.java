package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.dns.HostNameResolver;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.ManoConfigure;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BlueGreenUpdateSiteStrategy< Conf extends ClientConfiguration> extends WeightedUpdateSiteStrategy<Conf> {
	@Override
	public List<String> getAllSiteName() {
		List<String> allNames = otherSiteNames(mainSiteHostName);
		allNames.add(mainSiteHostName);
		return allNames.stream().filter(Objects::nonNull).collect(Collectors.toList());
	}
	@Override
	public List<String> otherSiteNames(String thisSiteName){
		return Stream.of(createPairHostname(thisSiteName)).filter(Objects::nonNull).collect(Collectors.toList());
	}
	public BlueGreenUpdateSiteStrategy(Site<Conf> mainSite) {
		super(mainSite);
		wconf = generateManoConfig();
	}

	public ManoConfigure generateManoConfig() {
		String otherName = createPairHostname(mainSiteHostName);
		if (otherName != null) { // legit serviceName has pair
			ManoConfigure.allManoConfigDistinct.computeIfAbsent(mainSiteHostName, k -> {
				ManoConfigure manoConfig = new ManoConfigure();
				Map<String, Integer> connectionCountMap = new ConcurrentHashMap<>();
				connectionCountMap.put(mainSiteHostName, 1);
				connectionCountMap.put(otherName, 0);
				manoConfig.setIWeightMap(connectionCountMap);
				// khong can quan ly resolve DNS o day, cho nao goi createManoConfig thi tu quan ly endpoints
				//	 chi can khoi tao de dung neu khong co 2 nettyclient cua blue va green
				return manoConfig;
			});
			return ManoConfigure.allManoConfigDistinct.get(mainSiteHostName);
		}
		return null; // serviceName is IPv4
	}

	@Override
	public void refresh() {
		/**
		 * Weight map is set statically, init with 1blue0green, then overwrite by weight. Read instantly, so no need to be refreshed
		 */
	}



	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.setLength(0);
		sb.append("Status: {");
		sb.append(wconf.weightStats());
		if (logger.isDebugEnabled()) {
			sb.append(wconf.keyRouter.stats());
			sb.append(destMap.values().stream()
				.map(site -> site.hostName + ": " + site.getRouteStrategyRef().get().report()
			).collect(Collectors.joining(",\n")));
		}
		sb.append("}");
		return sb.toString();
	}

	public static String createPairHostname(String serviceName) {
//		khong dung doc tu config, ve mat ngu nghia config khong chua update hostname, gia tri nay do mano cung cap hoac do noi suy
		if (ConnectionManager.getInstance().getConfiguration() != null) {
			if (serviceName.length() > ConnectionManager.getInstance().getConfiguration().manoUpdateSuffix.length()) {
				int maybeHeadLength = serviceName.length() - ConnectionManager.getInstance().getConfiguration().manoUpdateSuffix.length();
				String header = serviceName.substring(maybeHeadLength);
				if (Objects.equals(header, ConnectionManager.getInstance().getConfiguration().manoUpdateSuffix)) { // sN=green
					return header;
				} else /* this might be blue */ if (!HostNameResolver.isValidIPV4(serviceName))
					return serviceName + ConnectionManager.getInstance().getConfiguration().manoUpdateSuffix; // blue
			} else /* this might be blue */ if (!HostNameResolver.isValidIPV4(serviceName))
				return serviceName + ConnectionManager.getInstance().getConfiguration().manoUpdateSuffix; // blue
		}
		return null; // hoac ConnectionManager null hoac serviceName la Ipv4
	}
}

