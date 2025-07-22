package com.viettel.vocs.microchassis.serviceRegistry.codec;

import com.jsoniter.JsonIterator;
import com.jsoniter.output.JsonStream;
import com.jsoniter.spi.JsonException;
import com.jsoniter.spi.TypeLiteral;
import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tcp.codec.MsgHeader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

interface DecodeAll {
	void decode(String stringContent);

	String encode();
}

/**
 * @author tiennn18
 * after decode changes from server push, build this object represent for right order of client
 * contain all peer of this instance
 */

public class LBRMsg extends Msg implements DecodeAll {
	public static final String CLIENT_PROPKEY = "clients"; // treat all as multiple peer at once
	public static final String SERVER_PROPKEY = "servers"; // treat all as multiple peer at once

	public String states() {
		return String.format("LBRMsg has url=%s state=%s headers=%s servers=%s clients=%s content=%s",
			url, get(LBRMsg.STATE), headers(),
			servers.entrySet().stream().map(e -> String.format("%s:%s", e.getKey(), e.getValue().status())).collect(Collectors.joining(", ")),
			clients.entrySet().stream().map(e -> String.format("%s:%s", e.getKey(), e.getValue().reporTargets())).collect(Collectors.joining(", ")),
			this);
	}
	@Override
	public String getProtoVersionText() {
		return ChassisConst.SupportVersion.LBR;
	}
	public void reportPeers() {
		clients.forEach((id, client) -> logger.info("[client:{}] {}", id, client.targetMap));
		servers.forEach((id, server) -> logger.info("[server:{}] {}", id, server.targetMap));
	}


	static class ContentMap implements DecodeAll {
		/**
		 * this map must be Map<String, String>
		 * first is prop name, second is json serialized string
		 */
		public Map<String, String> contentMap = new ConcurrentHashMap<>();

		public <T> T decode(String prop, Class<T> className) {
			String content = contentMap.get(prop);
			try {
				return JsonIterator.deserialize(content, className);
			} catch (JsonException je) {
				return null;
			}
		}

		public <C extends IDfy> Map<String, C> decodeMap(String prop, Class<C> className) {
			String content = contentMap.get(prop);
			if (content != null && content.length() > 0) {
				return content.lines().map(line -> {
						try {
							return JsonIterator.deserialize(line, className);
						} catch (JsonException je) {
							logger.error(je);
							return null;
						}
					})
					.filter(Objects::nonNull)
					.collect(Collectors.toMap(IDfy::getId, o -> o));
			}
			return new HashMap<>();
		}

		public <C> List<C> decodeList(String prop, Class<C> className) {
			String content = contentMap.get(prop);
			if (content != null) {
				return content.lines().map(line -> {
						try {
							return JsonIterator.deserialize(line, className);
						} catch (JsonException je) {
							return null;
						}
					})
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			}
			return new ArrayList<>();
		}

		public <T extends IDfy> ContentMap encodeMap(String prop, Map<?, T> objs) {
			return encodeList(prop, objs.values());
		}

		public <T> ContentMap encodeList(String prop, Collection<T> objs) {
			contentMap.put(prop, objs.stream()
				.map(JsonStream::serialize)
				.collect(Collectors.joining("\n")));
			return this;
		}

		public <T> ContentMap encode(String prop, T objs) {
			contentMap.put(prop, JsonStream.serialize(objs));
			return this;
		}

		@Override
		public String encode() {
			return JsonStream.serialize(contentMap);
		}

		public static ContentMap parse(String contentMapString) {
			ContentMap c = new ContentMap();
			try {
				c.decode(contentMapString);
			} catch (JsonException ignored) {
			}
			return c;
		}

		public void decode(String contentMapString) {
			contentMap = JsonIterator.deserialize(contentMapString, new TypeLiteral<>() {
			});
		}
	}

	private static final Logger logger = LogManager.getLogger(LBRMsg.class);
	public static final String SERVICE_TYPE = "service-type";
	public static final String DNS_NAME = "DNS";
	/**
	 * state of represent LBRMsg of pod
	 * (client) init -> update or same -> (client) sync -> sendSync -> (server) processing -> register -> (server) true -> ctx response -> (client)
	 * interval ... update or same -> (client) sync -> sendSync -> (server) processing -> calculate -> (server) order -> ctx response -> (client) update -> apply to local client/server then read current state -> (client) sync -> ...
	 */
	public static final String STATE = "state";
	public final Map<String /*clientId*/, LBRClientInfo> clients = new ConcurrentHashMap<>();
	public final Map<String /*serverId*/, LBRServerInfo> servers = new ConcurrentHashMap<>();

	/**
	 * LBR is special TCP message without body, only JSON like fields in header and object props
	 * only call this constructor for LBRMsg.<role>Make<mgr>Msg() and represent LBRMsg obj
	 */
	public LBRMsg(ByteBufAllocator bytebufAllocator) { // for first init
		super(bytebufAllocator);
		headers.set(DNS_NAME, ConnectionManager.getMyDNS());
		headers.set(SERVICE_TYPE, CommonConfig.InstanceInfo.VDU_NAME.get());
		headers.set(STATE, "init");
//		validate(); // no need to validate, all set
	}

	public LBRMsg(ByteBuf content, String url, String msgId, MsgHeader headers) { // decoded msg
		super(content, url, msgId, headers);
		// cac header se duoc copy boi MsgDecode
	}
	public LBRMsg(ByteBufAllocator bytebufAllocator, String url, String msgId, MsgHeader headers) { // response on req id
		super(bytebufAllocator, url, msgId, headers);
	}

	public LBRMsg(ByteBufAllocator bytebufAllocator, String url, String msgId) { // mgr response msg
		super(bytebufAllocator, url, msgId);
	}
	public LBRMsg(ByteBufAllocator bytebufAllocator, String url) { // mgr response msg
		super(bytebufAllocator, url);
	}
	public LBRMsg(LBRMsg origin, boolean shareBuffer) { // for clone/copy msg
		super(origin, shareBuffer);
	}
	@Override
	public LBRMsg replicate() {
		try {
			dumpContent();
			LBRMsg newLbrMsg = new LBRMsg(this, false);
			newLbrMsg.copyAdvanceFrom(this);
			return newLbrMsg;
		} catch (NullPointerException ignored) {
			return null;
		}
	}

	public void copyAll(LBRMsg origin) {
		origin.dumpContent(); // dump to bytebuf
		super.copyAll(origin); // copy as a Msg for url, headers, messid, content
		decode(toString());
//		copyAdvanceFrom(origin); // no need to copy advance, because dumped and rebuilt by decoded
	}

	@Override
	public String toString() {
		return super.toString(Charset.defaultCharset());
	}

	public synchronized void copyAdvanceFrom(LBRMsg msg) {
		decode(msg.toString());
		msg.clients.forEach((clientId, client) -> {
			clients.putIfAbsent(clientId, client); // overwrite if havent record
			clients.get(clientId).copy(client); // copy if existed
		});
		msg.servers.forEach((serverId, server) -> {
			servers.putIfAbsent(serverId, server);
			servers.get(serverId).copy(server);
		});
	}


	@Override
	public void decode(String contentMapString) {
		ContentMap c = ContentMap.parse(contentMapString);
		c.decodeMap(CLIENT_PROPKEY, LBRClientInfo.class).forEach(clients::putIfAbsent);
		c.decodeMap(SERVER_PROPKEY, LBRServerInfo.class).forEach(servers::putIfAbsent);
	}

	@Override
	public String encode() {
		return (new ContentMap())
			.encodeMap(CLIENT_PROPKEY, clients)
			.encodeMap(SERVER_PROPKEY, servers)
			.encode();
	}

	public void dumpContent() { // need to call before send, best is in validate() method
		writeFrom(encode().getBytes());
	}

	public String get(String prop) {
		return headers.getString(prop);
	}

	public void setState(String newState) {
		headers.set(STATE, newState);
	}

	public String getState() {
		return headers.getString(STATE);
	}

	/**
	 * rotate message for reuse
	 */
	@Override
	public LBRMsg reuse(String url) {
		setUrl(url);
		setMessageId(newMsgId());
		return this;
	}

	public static LBRMsg clientMakeCreate(ByteBufAllocator bytebufAllocator) {
		return new LBRMsg(bytebufAllocator, MsgType.PING.getUrl(), MsgType.CREATE_MSG_ID);
	}

	public static LBRMsg clientMakePing(ByteBufAllocator bytebufAllocator) {
		return new LBRMsg(bytebufAllocator, MsgType.PING.getUrl());
	}

	public static LBRMsg serverMakeStop(ByteBufAllocator bytebufAllocator) {
		return new LBRMsg(bytebufAllocator, MsgType.SERVER_STOP.getUrl());
	}

	public void validateLbr() throws NullPointerException {
		dumpContent();
		Objects.requireNonNull(get(SERVICE_TYPE), "LBRMsg header SERVICE_TYPE is required");
		Objects.requireNonNull(get(DNS_NAME), "LBRMsg header DNS_NAME is required");
	}
}
