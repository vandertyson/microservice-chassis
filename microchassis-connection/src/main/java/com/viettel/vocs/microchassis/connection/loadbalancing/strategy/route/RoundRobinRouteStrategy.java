package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.route;

import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.exception.NoConnectionForStrategyToSelect;
import com.viettel.vocs.microchassis.connection.exception.RouteStrategyException;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.RR;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.common.os.TimeUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinRouteStrategy extends FrontPressureRouteStrategy {
	private final RR rrConfig;

	public static class RoundRobinEngine<T> extends RouteEngine {
		protected AtomicInteger pointer = new AtomicInteger(Integer.MAX_VALUE);

		public int nextPointer() {
			int curPointer;
			while ((curPointer = pointer.decrementAndGet()) < 0) {
				pointer.set(Integer.MAX_VALUE);
			}
			return curPointer;
		}
		public boolean isFinishScan(int beforeScanPointer, int listSize){
			return beforeScanPointer - currentPointer() >= listSize;
		}
		public int currentPointer(){
			return pointer.get();
		}
		public T getByNextPointer(List<T> sortList) { // sortList is snapshoted -> size not change, RR do not remove but overwrite
			while (sortList.size() > 0) {
				try {
					return sortList.get(nextPointer() % sortList.size()); // RR only based on this line :))
				} catch (IndexOutOfBoundsException ignored) { /*re-get*/ }
			}
			return null;
		}
	}

	private final RoundRobinEngine<Routable> engine = new RoundRobinEngine<>();

	public RoundRobinRouteStrategy(PeerConfig config, StrategyConfigure configure, Map<String, Routable> connections) {
		super(config, configure, connections);
		rrConfig = (RR) routeConfig;
	}

	@Override
	protected String statistics() {
		return String.format("{dest: {s: %d}, cache: {s: %d, dirtCache: %b, p: %d, l: %s}}",
			destMap.size(), cachedSortList.size(), cachedSortList.isEmpty() || cachedSortList.size() != destMap.size(), engine.pointer.get(), engine.createStringDetail(cachedSortList));
	}

	@Override
	public List<Routable> selectRoutesWithCached() throws RouteStrategyException {
		Routable selected;
		switch (cachedSortList.size()) {
			case 1:
				return cachedSortList; // for quick check
			case 0: // cache is carefully build, so trust it
				refresh(); // double check rebuild
				if (cachedSortList.isEmpty()) throw new NoConnectionForStrategyToSelect(this);
			default: // more than 1 conn -> check isWritable to increase success send rate
				long start = TimeUtils.nowNano();
				do {
					selected = engine.getByNextPointer(cachedSortList);
					if (selected == null)
						throw new NoConnectionForStrategyToSelect(this); // null when list empty while selecting
					if (!pendingRemove.contains(selected.getId()) && selected.isWritable()) return List.of(selected); // front pressure so only select 1 dest without a list to try acquire
				} while (!TimeUtils.isTimeoutNano(start, rrConfig.selectTimeoutNs));
				throw new NoConnectionForStrategyToSelect(this); // null when list empty while selecting
		}
	}

	@Override
	public void refresh() {
		checkCache();
	}

	@Override
	public void intervalRefresh() { /* nothing to continuous refresh */
		checkCache();
	}

	private void checkCache() {
		if (cachedSortList.size() == 0 || cachedSortList.size() != destMap.size()) {
			rebuildCacheSortList();
		}
	}
}
