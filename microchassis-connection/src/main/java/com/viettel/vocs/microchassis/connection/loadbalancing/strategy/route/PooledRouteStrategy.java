package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.route;

import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.exception.NoConnectionForStrategyToSelect;
import com.viettel.vocs.microchassis.connection.exception.RouteStrategyException;
import com.viettel.vocs.microchassis.connection.exception.SelectFailed;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.RPL;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.common.os.TimeUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PooledRouteStrategy extends RoundRobinRouteStrategy {
	/**
	 * RoundRobin based
	 * especially for sync clients, manage connection as pool for selecting.
	 * need combine with select Ext algorithm to well distribute load
	 * pool filter will first be applied, then Ext algorithm to choose
	 * <p>
	 * with pooled round-robin, it exactly is Pooled route strategy open full pool conns by connPerIP
	 * however, if claimed, connection will be blocked, which mean state.curCC > 0 can not be selected
	 */
	private final Object orderLock = new Object();
	private final AtomicBoolean entranLock = new AtomicBoolean(true);
	private final RPL rrPooledConfig;

	public PooledRouteStrategy(PeerConfig config, StrategyConfigure configure, Map<String, Routable> connections) {
		super(config, configure, connections);
		rrPooledConfig = (RPL) routeConfig;
	}

	protected static Stream<Routable> filterAvailDest(Stream<Routable> dests, Collection<String> removeIds) {
		return dests.filter(routable -> routable.checkAvailability() && !removeIds.contains(routable.getId()));
	}

	protected CopyOnWriteArrayList<Routable> getAvailDest() {
		return filterAvailDest(destMap.values().stream(), pendingRemove).collect(Collectors.toCollection(CopyOnWriteArrayList::new));
	}

	protected long countAvailDest() {
		return Math.max(0, destMap.values().stream().filter(Routable::checkAvailability).count() - pendingRemove.size());
	}

	@Override
	protected String statistics() {
		return String.format("{avail: {s: %d}}", countAvailDest());
	}

	@Override
	protected CopyOnWriteArrayList<Routable> rebuildCacheSortList() {

		CopyOnWriteArrayList<Routable> cachedSortList1 = getAvailDest(); // create this var to set and return the same object
		cachedSortList = cachedSortList1;
//		System.out.println("Rebuild cache " + cachedSortList.size());
		return cachedSortList1;
	}

	protected List<Routable> select1time() throws RouteStrategyException {
		long start = TimeUtils.nowNano();
		do {
			synchronized (orderLock) {
				if (entranLock.compareAndSet(true, false)) { // Check if lock can be acquired
					try {
						List<Routable> routables = super.selectRoutesWithCached();
						if (!routables.isEmpty()) {
							// contain
							Routable target = filterAvailDest(routables.stream(), pendingRemove).filter(Routable::checkUsable).findFirst().orElse(null);
							if (target != null) {
//								System.out.println("Select cached found");
								return List.of(target);
							}
						}
					} finally { // use try catch to protect the atomic entranLock
						entranLock.set(true);
					}
				}
			}
		} while (!TimeUtils.isTimeoutNano(start, ((RPL)routeConfig).select1TimeoutNs) && TimeUtils.waitSafeTick());
		throw new SelectFailed(this);
	}

	@Override
	// do not set this method sync here. it creates deadlock with sync of RR, use orderLock instead
	public List<Routable> selectRoutesWithCached() throws RouteStrategyException {
		long start = TimeUtils.nowNano();
		do {
			if (isConnected()) {
				try {
					return select1time();
				} catch (SelectFailed ignored) {
					rebuildCacheSortList();
				} // end of conn -> create and wait new
			}
		} while (!TimeUtils.isTimeoutNano(start, ((RPL) routeConfig).maxWait1SelectNs) && TimeUtils.waitSafeTick());
		throw new NoConnectionForStrategyToSelect(this);
	}

	@Override
	public void refresh() {
		// TODO define cache for pool, currently do nothing, choose from scratch
	}

	@Override
	public void intervalRefresh() {
		// TODO define cache for pool, currently do nothing, choose from scratch
	}


}
