package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.exception.UpdateStrategyException;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.WeightConfigure;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author tiennn18
 */
public abstract class WeightedUpdateSiteStrategy<Conf extends ClientConfiguration>
	extends UpdateStrategy<Conf> {
	protected WeightConfigure wconf; // maybe null, mean single site or skip UpdateStrategy, or temp client
	protected final Map<String, AtomicLong> weightCounterMap = new ConcurrentHashMap<>(); // map of valid weighted site, kinda states of wconf.weightMap

	protected WeightedUpdateSiteStrategy(Site<Conf> mainSite, WeightConfigure conf) {
		super(mainSite);
		wconf = conf;
	}

	protected WeightedUpdateSiteStrategy(Site<Conf> mainSite) {
		super(mainSite);
	}

	protected final AtomicLong totalCounter = new AtomicLong();

	@Override
	public UpdateSite selectSite(String key) throws UpdateStrategyException {
		String siteSelected = wconf.keyRouter.route(key);
		return siteSelected == null ? anySite() : destMap.get(siteSelected);
	}

	@Override
	public Site<Conf> anySite() throws UpdateStrategyException {
		if (weightCounterMap.size() > 0) {
			Optional<Map.Entry<String, AtomicLong>> dest = weightCounterMap.entrySet().stream().filter(e ->
					(float) e.getValue().get() / totalCounter.get() <= wconf.get(e.getKey()) // null safe, if weight null, equal 0.0f -> not selected
				// neu counter cua key nay chua dat weight thi select de gui tiep vao day
			).findFirst(); // da check weightCounterMap.size()
			if (dest.isPresent()) {
				dest.get().getValue().incrementAndGet();
				totalCounter.incrementAndGet();
				return destMap.get(dest.get().getKey());
			}
		}
		throw new UpdateStrategyException(this, "No site to select");
	}

	@Override
	public String toString() {
		return "Weighted[" + wconf.weightMap.toString() + "]";
	}

	@Override
	public void refresh() {
		// this may called at super constructor setDestMap -> call super.refresh -> call to this at the time this object havent constructed
		weightCounterMap.clear();
		if (enable) {
			wconf
				.weightMap
				.keySet()
				.stream()
				.filter(destMap::containsKey)
				.forEach(key -> weightCounterMap.put(key, new AtomicLong()));
			totalCounter.set(0);
		}
	}
}
