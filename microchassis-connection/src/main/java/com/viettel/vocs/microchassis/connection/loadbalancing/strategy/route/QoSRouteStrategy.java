package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.route;

import com.viettel.vocs.microchassis.connection.client.ClientChannelAttribute;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.exception.NoConnectionForStrategyToSelect;
import com.viettel.vocs.microchassis.connection.exception.RouteStrategyException;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.BackPressureConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class QoSRouteStrategy
	extends RouteStrategy {
	@Override
	public boolean isPressure() {
		return destMap.values().stream()
			.map(routable -> (BackPressureConnectionMode)(ClientChannelAttribute.getConnModeRef(routable.getChannel()).get()))
			.filter(Objects::nonNull)
			.allMatch(BackPressureConnectionMode::isUnderPressure); // if empty -> return true, mean stuck
	}
	protected QoSRouteStrategy(PeerConfig config, StrategyConfigure configure, Map<String, Routable> connections) {
		super(config, configure, connections);
	}

	AtomicBoolean isReduce = new AtomicBoolean(true);
	AtomicInteger scanCacheJoin = new AtomicInteger(1);
	private boolean scanCache() throws RouteStrategyException {
		if(cachedSortList.size() == 0) throw new NoConnectionForStrategyToSelect(this); // null safe // trust in cache
		if(scanCacheJoin.decrementAndGet() == 0) { // when reload cache ? when cache dirty ? => when score order change -> re-score => set to isReduce
			float preScore = Float.MAX_VALUE;
			for (Routable conn : cachedSortList) {
				BackPressureConnectionMode curMode = (BackPressureConnectionMode) conn.getConnMode();
				if (curMode.peerStrategyFailStack.get() > 0) continue; // skip
				float curScore = curMode.score();
				isReduce.set(curScore <= preScore);
				if (isReduce.get()) {
					preScore = curScore;
				} else break;
			}
			scanCacheJoin.set(1); // for other thread join
		}
		return isReduce.get();
	}
	@Override
	public List<Routable> selectRoutesWithCached() throws RouteStrategyException {
		return scanCache()
			? cachedSortList // all chain order ok reduce -> can use
			: scratchSelectRoutes(); // rebuild cache
	}

	@Override
	public List<Routable> scratchSelectRoutes() throws RouteStrategyException {
		CopyOnWriteArrayList<Routable> routables = rebuildCacheSortList(); // null safe
		if(routables.isEmpty()) throw new NoConnectionForStrategyToSelect(this);
		return routables;
	}
	public static String createStringDetail(Collection<Routable> src){
		return src.stream().map(r->String.format("%s@%s", r.getConnMode().report(), r.getEndpoint().ip)).collect(Collectors.joining(", "));
	}
	@Override
	protected String statistics() {
		return String.format("{cache: {s: %d, dLCC: {%s}, dest: {s: %d, csLCC: %s}}",
			cachedSortList.size(),
			createStringDetail(cachedSortList),
			destMap.size(),
			createStringDetail(destMap.values())
		);
	}

	private synchronized CopyOnWriteArrayList<Routable> sortScore() { // null safe
		CopyOnWriteArrayList<Routable> orderedListRoutable;
		switch (destMap.size()) {
			case 0:
				return new CopyOnWriteArrayList<>();
			case 1:
				orderedListRoutable = new CopyOnWriteArrayList<>(destMap.values());
				try{
					orderedListRoutable.get(0).getConnMode(); // check channel CMref
				} catch (NullPointerException | IndexOutOfBoundsException ignored){ // Null when channel die, CMref null, Idx when call values() destMap removed
					return new CopyOnWriteArrayList<>();
				}
				return orderedListRoutable;
			default:
				orderedListRoutable = destMap.values().stream()
					.map(conn -> {
						ConnectionMode connMode;
						try{
							if((connMode = conn.getConnMode()) == null) return null; // skip null CMref
						} catch (NullPointerException ignored){
							return null; // skip died channel
						}
						BackPressureConnectionMode curMode = (BackPressureConnectionMode) connMode;

						if (curMode.peerStrategyFailStack.get() > 0 && (curMode.peerStratFailRater.tryAndDraw())) return Pair.of(conn, 0f);
						// select connection with lowest utilization, when maxScore = Float.MAX_VALUE, dont have to check other
						float score = curMode.score();
						return score == 2f ? null : Pair.of(conn, 1f/score); // skip full load connection with score 2
					})
					.filter(Objects::nonNull)
					.sorted(Comparator.comparingDouble(Pair::getRight/*1f/score~util*/))
					.map(Pair::getLeft)
					.collect(Collectors.toCollection(CopyOnWriteArrayList::new));
				if (!orderedListRoutable.isEmpty()) return orderedListRoutable;
				return new CopyOnWriteArrayList<>();
		}
	}
	@Override
	protected CopyOnWriteArrayList<Routable> rebuildCacheSortList(){
		CopyOnWriteArrayList<Routable> cachedSortList1 = sortScore(); // null safe  // create this var to set and return the same object
		cachedSortList = cachedSortList1;
		return cachedSortList1;
	}

	@Override
	public void refresh() {

	}

	@Override
	public void intervalRefresh() {

	}

	@Override
	public String toString() {
		return "QoS placeholder";
	}
}
