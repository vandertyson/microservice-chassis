package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.route;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.exception.ConnectionException;
import com.viettel.vocs.microchassis.connection.exception.NoConnectionForStrategyToSelect;
import com.viettel.vocs.microchassis.connection.exception.RouteStrategyException;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.LCC;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ClientLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import com.viettel.vocs.microchassis.util.MonitorManager;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 */
public class LeastCCRouteStrategy extends FrontPressureRouteStrategy {
	private static int counter = 0;

	@Override
	public boolean isPressure() {
		return destMap.values().stream()
			.map(Routable::getCounter)
			.filter(Objects::nonNull)
			.allMatch(counter -> softCcThresholdEstimate(counter) < counter.getSentCC()); // if empty -> return true, mean stuck
	}

	private float softCcThresholdEstimate(PeerCounter counter) {
		return Math.max(1, counter.getSentTPS()) * counter.getAvgLatencyMs() / 1000f;
	}

	public static class LCCEngine extends RouteEngine {
		public CopyOnWriteArrayList<Routable> get0LCCs(Collection<Routable> src) { // cc cao thi tps thap -> check cai nay trc dc ma ko so cham
			if (src.size() == 0) return new CopyOnWriteArrayList<>();
			return src.stream()
				.filter(conn -> conn.getSentCC(true) == 0)
				.collect(Collectors.toCollection(CopyOnWriteArrayList::new));
		}

		public List<Routable> directSortAscLCC(List<Routable> src) {
			if (src.size() == 0) return src;
			//snapshot load, if this factor change, it would change the order of same CC,
			// like first compare A 4.51 vs B 4.37 C 4.67, => B->A->C then B update to 4.9 -> compare with D 4.1 make the order of D->A->B->C -> conflict state between previous BAC and current ABC => TimSort throw
			// => snapshot the order, then select by key, if not found like new add to view List<> src, then not found default by 0
			// ==> steady order
			return src.stream()
				.collect(Collectors.toMap(r -> r, r -> r.getPeerLoad() / 100_00f + r.getSentCC(true))) // snapshot CPU // snapshot CC // + peerLoadFloat to sort same CC, ex: 4.0 4.8 4.9543 for 3 conn has 4 CC with different peerLoad
				.entrySet().stream()
				.sorted(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
		}

		public List<Routable> copySortAscLCC(Collection<Routable> src) {
			return directSortAscLCC(new CopyOnWriteArrayList<>(src));
		}

		public Routable leastCC(Collection<Routable> src) {
			if (src.size() == 0) return null;
			try {
				return copySortAscLCC(src).get(0);
			} catch (IndexOutOfBoundsException e) {
				// list may change during get
				return null;
			}
		}

		private final AtomicLong cacheHit = new AtomicLong(0);
		private final AtomicLong cacheMiss = new AtomicLong(0);

		public long hitCache() {
			return cacheHit.incrementAndGet();
		}

		public long missCache() {
			return cacheMiss.incrementAndGet();
		}

		public float cacheHitRate() {
			float hit = cacheHit.get();
			float total = hit + cacheMiss.get();
			return total > 0 ? hit / total : 0;
		}
	}

	protected final LCCEngine engine = new LCCEngine();

	public LeastCCRouteStrategy(PeerConfig config, StrategyConfigure configure, Map<String, Routable> connections) {
		super(config, configure, connections);
	}


	@Override
	protected String statistics() {
		return String.format("{cache: {s: %d, r: %.2f, dLCC: {%s}, dest: {s: %d, csLCC: %s}}",
			cachedSortList.size(),
			engine.cacheHitRate(),
			RouteEngine.createStringDetail(cachedSortList),
			destMap.size(),
			RouteEngine.createStringDetail(engine.copySortAscLCC(destMap.values()))
		);
	}

	@Override
	public synchronized List<Routable> scratchSelectRoutes() throws RouteStrategyException {
		/**
		 * random in diff range algorithm:
		 * if avgCC changed by debounce -> this may flush all to 1 conn under 1 cycle of debounce
		 * 	if we wanna maintain absolute diff between highest and lowest amount of k%
		 * 	so choose with threshold of 100%-k% value, at least 1, then route to it
		 * 		after a bounce cycle, if only 1 conn always choosed at the last cycle, avgCC peak -> all other conn is selected for random routing at next cycle
		 * 		hence reduce the diff to at least k% between the peak
		 * 	100% is from lowest to highest, not from 0 to highest
		 * 	k%=50%
		 */
		switch (destMap.size()) {
			case 1:
				return new ArrayList<>(destMap.values()); // for quick check, if channel null will throw at send
			case 0:
				throw new NoConnectionForStrategyToSelect(this);
			default:
				return LCCdirectSelect();
		}
	}

	private List<Routable> LCCdirectSelect() throws RouteStrategyException {
		try {
			List<Routable> copySortAscLCC = engine.copySortAscLCC(destMap.values());
			cachedSortList = engine.get0LCCs(copySortAscLCC); // update cache
			Routable routable = copySortAscLCC.get(0); // if conn channel is null while selecting -> to the tail -> always selectable
			if (routable.getChannel() != null) return List.of(routable);
			// else min LCC conn is a max int -> all max int or all channel null // => throw NoConn below
		} catch (IndexOutOfBoundsException ignored) { // change destMap to empty during getting
			// => throw NoConn below
		}
		throw new NoConnectionForStrategyToSelect(this);
	}
	protected MonitorManager mon = MonitorManager.getInstance();
	@Override
	public List<Routable> selectRoutesWithCached() throws RouteStrategyException {
		long startSelect = TimeUtils.nowNano();
		switch (destMap.size()) {
			case 1:
				engine.hitCache();
				return new ArrayList<>(destMap.values()); // for quick check, if channel null will throw at send
			case 0:
				throw new NoConnectionForStrategyToSelect(this);
			default:
				try {
					if(cachedSortList.size() > 0) {
						List<Routable> remove = List.of(cachedSortList.remove(0));
						engine.hitCache();
						mon.lccCacheHit(startSelect);
						return remove; // this gud when much more dest or low TPS, make LCC o(1) when cache hit
					} else {
						engine.missCache();
						List<Routable> routables = LCCdirectSelect();
						mon.lccCacheMiss(startSelect);
						return routables;
					}
				} catch (IndexOutOfBoundsException ignored) { // consumed all caches by other threads
					engine.missCache();
					List<Routable> routables = LCCdirectSelect();
					mon.lccCacheMiss(startSelect);
					return routables; // if no 0CC conn -> re-select as pure LCC // this include recreate cache for other req use
					// else has new cache list, re-select by remove(0)
				}
		}
	}

	@Override
	protected CopyOnWriteArrayList<Routable> rebuildCacheSortList() {
		return cachedSortList = engine.get0LCCs(destMap.values()); // cache is list of 0CC
	}

	@Override
	public void refresh() { /*based on avgCC of dest so no need to refresh*/}

	@Override
	public void intervalRefresh() { /* nothing to continuous refresh */ }

	public static void main(String[] args) throws RouteStrategyException {
		ClientConfiguration config = new ClientConfiguration("fake");
		config.routeStrategy = new LCC();
		LeastCCRouteStrategy strategy = new LeastCCRouteStrategy(config, config.routeStrategy, new ConcurrentHashMap<>());
		for(int i = 0; i < 100; i++){
			Routable routable = new Routable() {
				Random random = new Random();

				@Override
				public boolean isConnected() {
					return true;
				}

				@Override
				public void waitFlushAllMsg() {

				}

				@Override
				public boolean isInDnsResolve() {
					return true;
				}

				@Override
				public boolean isSendable() {
					return true;
				}

				@Override
				public long getSentCC(boolean recent) {
					return random.nextInt(50);
				}

				@Override
				public boolean isEnable() {
					return true;
				}

				@Override
				public String getChannelString() {
					return "0xabcd";
				}

				@Override
				public boolean isWritable() {
					return true;
				}

				@Override
				public void processTimeoutMsgId(Channel sendChannel, String msgId) {

				}

				@Override
				public void deregister() {

				}

				@Override
				public boolean checkAvailability() {
					return true;
				}

				@Override
				public void close() {

				}

				@Override
				public ConnectionMode skipWarmUp() {
					return null;
				}

				@Override
				public ConnectionMode checkMode() {
					return null;
				}

				@Override
				public int warmingUpTPS() {
					return 0;
				}

				@Override
				public PeerCounter getCounter() {
					return null;
				}

				@Override
				public ClientLimiter getLimiter() {
					return null;
				}

				@Override
				public String getId() {
					return String.valueOf(TimeUtils.nowNano());
				}

				@Override
				public int getPeerLoad() {
					return 80;
				}

				@Override
				public String ip() {
					return null;
				}

				@Override
				public boolean tryAccquire() {
					return true;
				}

				@Override
				public void acquire(TimeUtils.NanoBeacon startBeacon) {

				}

				@Override
				public boolean checkUsable() {
					return true;
				}

				@Override
				public void setEnable(boolean b) {

				}

				@Override
				public AtomicReference<CONNECTION_STATUS> getState() {
					return null;
				}

				@Override
				public ConnectionMode getConnMode() {
					return null;
				}

				@Override
				public void triggerRegister() {

				}

				@Override
				public void setDeleteMarker(boolean b) {

				}

				@Override
				public boolean isDeleteMarker() {
					return false;
				}

				@Override
				public boolean isDisableRoute() {
					return false;
				}

				@Override
				public ClientConfiguration getConfig() {
					return null;
				}

				@Override
				public void onChannelRenew(Channel newChannel) {

				}

				@Override
				public void onChannelDown() {

				}

				@Override
				public String getHostName() {
					return null;
				}

				@Override
				public Integer getPort() {
					return 0;
				}

				@Override
				public void connect(int retry) throws ConnectionException {

				}

				@Override
				public boolean isClosed() {
					return false;
				}
				Channel channel = new Channel() {
					@Override
					public ChannelId id() {
						return null;
					}

					@Override
					public EventLoop eventLoop() {
						return null;
					}

					@Override
					public Channel parent() {
						return null;
					}

					@Override
					public ChannelConfig config() {
						return null;
					}

					@Override
					public boolean isOpen() {
						return false;
					}

					@Override
					public boolean isRegistered() {
						return false;
					}

					@Override
					public boolean isActive() {
						return false;
					}

					@Override
					public ChannelMetadata metadata() {
						return null;
					}

					@Override
					public SocketAddress localAddress() {
						return null;
					}

					@Override
					public SocketAddress remoteAddress() {
						return null;
					}

					@Override
					public ChannelFuture closeFuture() {
						return null;
					}

					@Override
					public boolean isWritable() {
						return false;
					}

					@Override
					public long bytesBeforeUnwritable() {
						return 0;
					}

					@Override
					public long bytesBeforeWritable() {
						return 0;
					}

					@Override
					public Unsafe unsafe() {
						return null;
					}

					@Override
					public ChannelPipeline pipeline() {
						return null;
					}

					@Override
					public ByteBufAllocator alloc() {
						return null;
					}

					@Override
					public Channel read() {
						return null;
					}

					@Override
					public Channel flush() {
						return null;
					}

					@Override
					public ChannelFuture bind(SocketAddress localAddress) {
						return null;
					}

					@Override
					public ChannelFuture connect(SocketAddress remoteAddress) {
						return null;
					}

					@Override
					public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
						return null;
					}

					@Override
					public ChannelFuture disconnect() {
						return null;
					}

					@Override
					public ChannelFuture close() {
						return null;
					}

					@Override
					public ChannelFuture deregister() {
						return null;
					}

					@Override
					public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
						return null;
					}

					@Override
					public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
						return null;
					}

					@Override
					public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
						return null;
					}

					@Override
					public ChannelFuture disconnect(ChannelPromise promise) {
						return null;
					}

					@Override
					public ChannelFuture close(ChannelPromise promise) {
						return null;
					}

					@Override
					public ChannelFuture deregister(ChannelPromise promise) {
						return null;
					}

					@Override
					public ChannelFuture write(Object msg) {
						return null;
					}

					@Override
					public ChannelFuture write(Object msg, ChannelPromise promise) {
						return null;
					}

					@Override
					public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
						return null;
					}

					@Override
					public ChannelFuture writeAndFlush(Object msg) {
						return null;
					}

					@Override
					public ChannelPromise newPromise() {
						return null;
					}

					@Override
					public ChannelProgressivePromise newProgressivePromise() {
						return null;
					}

					@Override
					public ChannelFuture newSucceededFuture() {
						return null;
					}

					@Override
					public ChannelFuture newFailedFuture(Throwable cause) {
						return null;
					}

					@Override
					public ChannelPromise voidPromise() {
						return null;
					}

					@Override
					public <T> Attribute<T> attr(AttributeKey<T> attributeKey) {
						return null;
					}

					@Override
					public <T> boolean hasAttr(AttributeKey<T> attributeKey) {
						return false;
					}

					@Override
					public int compareTo(@NotNull Channel channel) {
						return 0;
					}
				};
				@Override
				public Channel getChannel() {
					return channel;
				}

				@Override
				public boolean ping(ByteBufAllocator allocator) {
					return false;
				}

				@Override
				public String getMonitorID() {
					return String.valueOf(TimeUtils.nowNano());
				}

				@Override
				public Endpoint getEndpoint() {
					return null;
				}
			};
			strategy.destMap.put(routable.getMonitorID(), routable);
		}
		long startTest = TimeUtils.nowNano();
		long nStep = 1000000;
		for(int test = 0; test < nStep; test++) {
//			List<Routable> rtesult =
			strategy.selectRoutesWithCached();
//			try {
//				rtesult.remove(200);
//			} catch (IndexOutOfBoundsException ignored){}
		}
		System.out.println(TimeUnit.NANOSECONDS.toMicros((TimeUtils.nowNano() - startTest)/nStep));
		// 100k test 1000 Routable list sort trong 137microS avg each time call
	}

	private static List<Routable> sort(LCCEngine engine, Map<String, Routable> destMap) throws NoConnectionForStrategyToSelect {
		CopyOnWriteArrayList<Routable> cachedSortList;
		try {
			if (counter ++ % 10 == 0){
				return new ArrayList<>();
			}
			List<Routable> copySortAscLCC = engine.copySortAscLCC(destMap.values());
			cachedSortList = engine.get0LCCs(copySortAscLCC); // update cache
			Routable routable = copySortAscLCC.get(0); // if conn channel is null while selecting -> to the tail -> always selectable
			return new ArrayList<>(List.of(routable));
			// else min LCC conn is a max int -> all max int or all channel null // => throw NoConn below
		} catch (IndexOutOfBoundsException ignored) { // change destMap to empty during getting
			// => throw NoConn below
		}
		throw new NoConnectionForStrategyToSelect(null);
	}
}
