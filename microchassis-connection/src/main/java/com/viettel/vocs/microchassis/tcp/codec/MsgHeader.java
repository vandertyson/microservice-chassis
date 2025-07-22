package com.viettel.vocs.microchassis.tcp.codec;

import com.viettel.vocs.microchassis.codec.handler.Handler;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MsgHeader
	extends ConcurrentHashMap<String, Object>
	implements Iterable<Map.Entry<String, Object>>{
	/**
	 * Header for TCP message (ByteBuf content)
		* local are values that not string
		*/
	public MsgHeader(){}
	public MsgHeader(Map<String, Object> source){
		putAll(source);
	}
	public static final String MESH_HEADER = "mesh"; // only contain in CREATE msg
	public static final String LOAD_HEADER = "load";
	public static final String SERVICE_NAME = "service_name";
	public static final String VDU_NAME = "vdu";
	public static final String SERVER_PUSH = "spush";
	public boolean isServerPush() {
		return get(SERVER_PUSH) != null;
	}
	public ConcurrentHashMap<String, String> getStringHeaders() { // null safe for each item and return map
		ConcurrentHashMap<String, String> sHeaders = new ConcurrentHashMap<>();
		entrySet().stream().filter(entry -> entry.getValue() instanceof String).forEach(e-> sHeaders.put(e.getKey(), (String) e.getValue()));
		return sHeaders;
	}
	/*
	 * Cac ham nao tra ve String co ten la <method>Header(String key, ...)
	 * Cac ham get header cu the can co 1 phien ban <method>(String key, ...) de tra ve Object, // more generic for local headers too
	 */
	public <T> T pop(String header, T defaultValue) {
		try {
			// Check if the key exists
			Object value = remove(header);
			if (value == null) return defaultValue;
			return (T) value;
		} catch (NullPointerException | ClassCastException e) {
			return defaultValue;
		}
	}
	public <T> T pop(String header) { //use for peer2peer header, transmitted so this header is String
		try {
			// Check if the key exists
			Object value = remove(header);
			return (T) value;
		} catch (NullPointerException | ClassCastException e) {
			return null;
		}
	}
	public String popString(String header) { //use for peer2peer header, transmitted so this header is String
		Object headerVal = pop(header);
		return (headerVal instanceof String /*included check null*/) ? (String) headerVal : null;
	}


	public Object get(String header) { // need this bcause get of concurrent hash map use Object key
		return super.get(header);
	}

	public String getString(String header) {
		Object headerVal = get(header);
		if (headerVal instanceof String /*included check null*/) return (String) headerVal;
		return null;
	}
	public void set(String key, Object val){
		super.put(key, val);
	}
	public <CastType> void onHeader(String key, Handler<CastType> onHeaderVal){
		Object rawVal = get(key);
		if (rawVal != null) {
			try {
				onHeaderVal.handle((CastType) rawVal);
			} catch (ClassCastException e){
				onHeaderVal.handle(null);
			}
		}
	}
	public <CastType> void onHeaderNotNull(String key, Handler<CastType> onHeaderVal) {
		Object val = get(key);
		if (val != null) {
			try {
				onHeaderVal.handle((CastType) val);
			} catch (ClassCastException ignored){
			}
		}
	}


	@NotNull
	@Override
	public Iterator<Entry<String, Object>> iterator() {
		return entrySet().iterator();
	}

	@Override
	public void forEach(Consumer<? super Entry<String, Object>> action) {
		entrySet().forEach(action);
	}

	@Override
	public Spliterator<Entry<String, Object>> spliterator() {
		return Iterable.super.spliterator();
	}

	public void setStatus(HttpResponseStatus statusCode) {
		if(statusCode!=null) set("status", statusCode.codeAsText().toString());
	}
	public String getStatus() {
		String statusInHeader = getString("status");
		return statusInHeader != null ? statusInHeader : "200";
	}
}
