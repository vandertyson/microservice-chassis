package com.viettel.vocs.microchassis.connection.loadbalancing.configure;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tiennn18
 */
public class ManoConfigure extends WeightConfigure {
	public final static Map<String, ManoConfigure> allManoConfigDistinct = new ConcurrentHashMap<>();
	public static ManoConfigure getConfig(String mainSiteHostName){
		return allManoConfigDistinct.get(mainSiteHostName);
	}
	public static ManoConfigure removeConfig(String mainSiteHostName){
		return allManoConfigDistinct.remove(mainSiteHostName);
	}

	public static float getWeightFromManoConfig(String serviceName) {
		ManoConfigure config = getConfig(serviceName);
		return (config != null)
			? config.get(serviceName, 0)
			: 0;
	}

	@Override
	public String toString() {
		return weightMap.toString();
	}

	public ManoConfigure() {
		super(); // no warmup between site
		// this Configure instance shared between both blue and green client
		// 2 client identified by service name;
		// serviceName can be initiated by blue or green
		// khong khoi tao gi o day, dung class ManoConfigure
	}

	public void setSite(String serviceName, float weight, float weightScaleMin, float weightScaleMax){
		if (weightScaleMax > weightScaleMin && weight > weightScaleMin){
			float range = weightScaleMax - weightScaleMin;
			weight -= weightScaleMin;
			addWeight(serviceName, Math.min(weight, range)/range);
		}
		throw new InvalidParameterException(String.format("Weight scale invalid [%.2f->%.2f]", weightScaleMin, weightScaleMax));
	}
	public void setSite(String serviceName, float weight){
		setSite(serviceName, weight, 0, 100);
	}
	public <EndpointItem> void setSite(String serviceName, List<EndpointItem> endpoints) {
		//update weight: trong so theo so luong connection
		set(serviceName, endpoints.size()); // endpoint list null safe -> isEmpty ~ 0
	}

}
