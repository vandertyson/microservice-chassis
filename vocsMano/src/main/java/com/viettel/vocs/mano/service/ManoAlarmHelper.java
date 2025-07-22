package com.viettel.vocs.mano.service;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.connection.client.ClientConnection;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import com.viettel.vocs.microchassis.connection.event.ClientEvent;
import com.viettel.vocs.microchassis.connection.event.ConnectionEvent;
import com.viettel.vocs.microchassis.connection.event.EventCatalog;
import com.viettel.vocs.microchassis.connection.event.InternalEvent;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.viettel.vocs.microchassis.connection.event.ConnectionEvent.EventType.*;

public interface ManoAlarmHelper {
	Logger logger = LogManager.getLogger(ManoAlarmHelper.class);

	Handler<Alarm> handler = a -> {
		logger.info("Alarm is resolved but still cache. Force resolve. {})", a);
		a.resolve();
	};
	Map<String, NettyClient> mapClient = new ConcurrentHashMap<>();
	List<ToWatch> watching = new CopyOnWriteArrayList<>();

	String SERVICE_IDENTIFIER = "SERVICE";
	String CONNECTION_IDENTIFIER = "CONNECTION";
	AtomicLong force = new AtomicLong();

	static void removeWatch(Alarm a) {
		for (ManoAlarmHelper.ToWatch watch : ManoAlarmHelper.watching) {
			if (Objects.equals(a, watch)) {
				ManoAlarmHelper.watching.remove(watch);
				if (logger.isDebugEnabled())
					logger.debug("Remove alarm watch. alarm={}, liveTimeMs={}, size={}",
						watch, ManoAlarmHelper.watching.size(), System.currentTimeMillis() - watch.getRaisedTime());
			}
		}
	}


	class ToWatch extends Alarm {
		private final long raisedTime;

		public ToWatch(Alarm a) {
			this(a, System.currentTimeMillis());
		}
		public ToWatch(Alarm a, long raisedTime) {
			super(a.alarmCode, a.resolveKey);
			this.raisedTime = raisedTime;
		}

		public long getRaisedTime() {
			return raisedTime;
		}
	}

	//event, clientId, serviceName
	private static Triple<EventCatalog, String, String> resolveKeyToEvent(String key) {
		Triple<EventCatalog, String, String> ret = null;
		try {
			String[] split = key.split(ChassisConfig.ManoConfig.AlarmConfig.ALARM_CACHING_KEY_SEPARATOR.get());
			if (split.length == 4) {
				String clientId = split[0];
				String serviceName = split[1];
				String identifier = split[2];
				String eventString = split[3];
				EventCatalog event = null;
				if (SERVICE_IDENTIFIER.equals(identifier)) {
					if (ChassisConst.Name.INTERNAL_CONNECTION_SERVICE_DOWN.name().equals(eventString)) {
						event = ClientEvent.EventType.CONNECT_FAIL;
					}
					if (ChassisConst.Name.INTERNAL_SERVICE_DISCOVERY_FAIL.name().equals(eventString)) {
						event = ClientEvent.EventType.DISCOVERY_FAIL;
					}
				}
				if (CONNECTION_IDENTIFIER.equals(identifier)) {
					if (ChassisConst.Name.INTERNAL_CONNECTION_DISCONNECT.name().equals(eventString)) {
						event = CONNECTION_DOWN;
					}
					if (ChassisConst.Name.INTERNAL_CONNECTION_CREATED_FAIL.name().equals(eventString)) {
						event = CONNECTION_INIT_FAIL;
					}
					if (ChassisConst.Name.INTERNAL_CONNECTION_EXCEPTION.name().equals(eventString)) {
						event = CONNECTION_WRITE_FAIL;
					}
				}
				ret = new ImmutableTriple<>(event, clientId, serviceName);
			}
		} catch (Throwable ex) {
			logger.error(ex, ex);
		}
		return ret;
	}

	static String eventToResolveKey(InternalEvent param) {
		if (param instanceof ClientEvent) {
			ClientEvent clientEventParam = (ClientEvent) param;
			ClientEvent.EventType sourceEvent = clientEventParam.getType();
			String client_id = clientEventParam.getClientId();
			String eventKey = "UNKNOWN_SERVICE_EVENT";
			switch (sourceEvent) {
				case CONNECT_SUCCESS:
				case CONNECT_FAIL:
					eventKey = ChassisConst.Name.INTERNAL_CONNECTION_SERVICE_DOWN.name();
					break;
				case DISCOVERY_SUCCESS:
				case DISCOVERY_FAIL:
					eventKey = ChassisConst.Name.INTERNAL_SERVICE_DISCOVERY_FAIL.name();
					break;
				default:
					break;
			}
			return buildCachingKey(client_id, clientEventParam.getServiceName(), SERVICE_IDENTIFIER, eventKey);
		}
		if (param instanceof ConnectionEvent) {
			ConnectionEvent connectionEventParam = (ConnectionEvent) param;
			ConnectionEvent.EventType sourceEvent = connectionEventParam.getType();
			ClientConnection clientConnection = connectionEventParam.getConnection();
			String eventKey = "UNKNOWN_CONNECTION_EVENT";
			switch (sourceEvent) {
				case CONNECTION_UP:
				case CONNECTION_DOWN:
					eventKey = ChassisConst.Name.INTERNAL_CONNECTION_DISCONNECT.name();
					break;
				case CONNECTION_WRITE_FAIL:
				case CONNECTION_WRITE_SUCCESS:
				case HEALTH_CHECK_FAIL:
				case HEALTH_CHECK_SUCCESS:
					eventKey = ChassisConst.Name.INTERNAL_CONNECTION_EXCEPTION.name();
					break;
				case CONNECTION_INIT_FAIL:
				case CONNECTION_INIT_SUCCESS:
					eventKey = ChassisConst.Name.INTERNAL_CONNECTION_CREATED_FAIL.name();
					break;
				case TIMEOUT_ISOLATION:
					eventKey = ChassisConst.Name.ISOLATION_CHANNEL_OVER_TIMEOUT_REQUEST.name();
					break;
			}
			return buildCachingKey(clientConnection.getClientId(), clientConnection.getBaseServiceName(), CONNECTION_IDENTIFIER, eventKey);
		}
		return param.type.toString();
	}



	static String buildCachingKey(String... params) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < params.length; i++) {
			sb.append(params[i]);
			if (i != params.length - 1) {
				sb.append(ChassisConfig.ManoConfig.AlarmConfig.ALARM_CACHING_KEY_SEPARATOR.get());
			}
		}
		return sb.toString();
	}


	static void addClientResolveWatcher(NettyClient client) {
		mapClient.put(client.getId(), client);
	}

	static void addWatch(Alarm a) {
		Triple<EventCatalog, String, String> triple = resolveKeyToEvent(a.getResolveKey());
		if (triple == null) return;
		String clientID = triple.getMiddle();
		NettyClient nettyClient = mapClient.get(clientID);
		if (nettyClient == null) return;
		ToWatch watch = new ToWatch(a);
		watching.add(watch);
		if (logger.isDebugEnabled()) {
			logger.debug("Add alarm watch. alarm={}, size={}", watch, watching.size());
		}
	}


	static String report() {
		return String.format("ManoAlarmHelper{mapClient=%d, forceResolve=%d, watching=%d}",
			mapClient.size(), force.get(), watching.size());
	}

	static String clearWatching() {
		String collect = watching.stream().map(f -> {
			Map<String, Object> info = new HashMap<>();
			info.put("alarmCode", f.alarmCode);
			info.put("resolveKey", f.resolveKey);
			info.put("raisedTime", TimeUtils.miliToString(f.getRaisedTime()));
			return f.toString();
		}).collect(Collectors.joining("\n"));
		logger.info("Clear alarm helper watching. size={}, content\n{}", watching.size(), collect);
		watching.clear();
		return collect;
	}
	Thread t = new Thread(() -> {
		while (TimeUtils.waitSafeMili(ChassisConfig.ManoConfig.AlarmConfig.SLEEP_PER_CHECK_MS.get())) {
			try {
				for (ToWatch watch : watching) {
					long liveTime = (System.currentTimeMillis() - watch.getRaisedTime()) / 1000;
					try {
						String resolveKey = watch.getResolveKey();
						if (logger.isDebugEnabled()) {
							logger.debug("Check unresolved alarm. code={}, resolveKey={}, timeMs={}",
								watch.getAlarmCode(), watch.getResolveKey(), System.currentTimeMillis() - watch.getRaisedTime());
						}
						Triple<EventCatalog, String, String> triple = resolveKeyToEvent(resolveKey);
						if (triple == null) {
							continue;
						}
						String clientID = triple.getMiddle();
						NettyClient nettyClient = mapClient.get(clientID);
						if (nettyClient == null) {
							continue;
						}
						EventCatalog event = triple.getLeft();
						String serviceName = triple.getRight();
						boolean resolved = false;
						if (event == null) {
							logger.error("Can not find event for id={}, code={}, resolveKey={}, timeMs={}",
								clientID, watch.getAlarmCode(), watch.getResolveKey(), System.currentTimeMillis() - watch.getRaisedTime());
							resolved = true;
						} else {
							resolved = nettyClient.isResolved(event, serviceName);
						}
						if (logger.isDebugEnabled()) {
							logger.debug("Check unresolved client alarm. id={}, serviceName={}, resolveKey={}, resolved={}, timeMs={}",
								clientID, serviceName, watch.getResolveKey(), resolved, System.currentTimeMillis() - watch.getRaisedTime());
						}
						if (resolved && liveTime > ChassisConfig.ManoConfig.AlarmConfig.MAXIMUM_RESOLVE_WAIT_SEC.get()) {
							handler.handle(new Alarm(watch.getAlarmCode(), watch.getResolveKey()));
							watching.remove(watch);
							long l = force.incrementAndGet();
							logger.info("Force resolve alarm #{}. liveTimeSec={}, alarmCode={}, resolveKey={}",
								l, liveTime, watch.getAlarmCode(), watch.getResolveKey());
						}
					} catch (Throwable e) {
						logger.error(e, e);
						if (liveTime > ChassisConfig.ManoConfig.AlarmConfig.MAXIMUM_RESOLVE_WAIT_SEC.get()) {
							handler.handle(new Alarm(watch.getAlarmCode(), watch.getResolveKey()));
							watching.remove(watch);
							long l = force.incrementAndGet();
							logger.info("Unknown alarm timeout. Resolve alarm #{}. liveTimeSec={}, alarmCode={}, resolveKey={}",
								l, liveTime, watch.getAlarmCode(), watch.getResolveKey());
						}
					}
				}
			} catch (Throwable ex) {
				logger.error(ex, ex);
			}
		}
	});
	static void start(){
		if(!t.isAlive()) {
			t.setName("mano_alarm_watch");
			t.start();
		}
	}
}
