package com.viettel.vocs.microchassis.connection.loadbalancing.configure;

import com.viettel.vocs.common.hashing.MurmurHash;
import com.viettel.vocs.common.hashing.NodeRouter;
import com.viettel.vocs.common.hashing.RendezvousNodeRouter;
import com.viettel.vocs.common.hashing.WeightedNode;
import com.viettel.vocs.common.hashing.WeightedRendezvousComputer;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 */
public class WeightConfigure extends FrontConfigure {
	public Map<String, Float> weightMap; // positive #0, stripped any not valid
	public NodeRouter<WeightedNode> keyRouter; // chi can refresh khi co thay doi weight trong manoConfig
	public String weightStats() {
		return "mapWeight=" + weightMap;
	}
	public long countClients(){
		return weightMap.values().stream().filter(d -> d>0).count();
	}
	public WeightConfigure() {
		this(new HashMap<>());
	}
	public boolean hasWeight(String key) {
		return weightMap.containsKey(key) && get(key, 0) > 0;
	}
	public WeightConfigure(Map<String, Float> weightMap) {
		super();
		setWeightMap(weightMap);
	}

	@Override
	public boolean diff(StrategyConfigure obj) {
		if(super.diff(obj)) return true;
		if(!( obj instanceof WeightConfigure)) return true;
		WeightConfigure o = (WeightConfigure) obj;
		return !Objects.equals(weightMap, o.weightMap);
	}

	protected void rebuildKeyRouter() {
		AtomicInteger count = new AtomicInteger();
		keyRouter = RendezvousNodeRouter.create(
			weightMap.entrySet().stream()
				.map(e -> new WeightedNode(count.incrementAndGet(),
					e.getValue(), // siteWeight
					e.getKey() // siteName
				))
				.collect(Collectors.toList()),
			MurmurHash::hash64, WeightedRendezvousComputer.create());
	}
	public synchronized WeightConfigure addWeight(String newTarget, float newTarget1factorWeight){
		if(newTarget1factorWeight>1) throw new InvalidParameterException("Add weight failed with weight greater than 100% ["+newTarget1factorWeight+"]");
		// add to 1factor weight, all existed rescale to 1-newWeight
		// sum
		float existRescaleFactor = 1-newTarget1factorWeight;
		weightMap = weightMap.entrySet().stream().filter(e -> e.getValue()>0)
			.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / existRescaleFactor));
		weightMap.put(newTarget, newTarget1factorWeight);
		setWeightMap(weightMap); // resolve residual of div and sum
		return this;
	}
	public synchronized WeightConfigure setWeightMap(Map<String, Float> weightMap) {
		// rescale to 1 factor
		float totalWeight = (float) weightMap.values().stream().filter(d -> d>0).mapToDouble(d -> d).sum();
		this.weightMap = weightMap.entrySet().stream().filter(e -> e.getValue()>0)
			.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / totalWeight));
		rebuildKeyRouter();
		return this;
	}
	public WeightConfigure setIWeightMap(Map<String, Integer> manoWeightmap) {
		// count weight map to scale weight map
		return setWeightMap(manoWeightmap.entrySet().stream().filter(e -> e.getValue() > 0)
			.collect(Collectors.toMap(Map.Entry::getKey, e -> (float) e.getValue())));
	}
	public float get(String connectionId) {
		return weightMap.get(connectionId);
	}
	public void set(String connectionId, float value) {
		weightMap.put(connectionId, value);
		rebuildKeyRouter();
	}
	public float get(String connectionId, float defaultValue) {
		return weightMap.getOrDefault(connectionId, defaultValue);
	}
	public boolean exists() {
		return weightMap == null || weightMap.isEmpty();
	}

	public void clear() {
		weightMap.clear();
		rebuildKeyRouter();
	}

}
