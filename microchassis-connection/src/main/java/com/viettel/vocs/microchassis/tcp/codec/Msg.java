/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.tcp.codec;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Msg
	extends ChassisMgrableMsg<MsgHeader, String, Object> {
	public static final Logger logger = LogManager.getLogger(Msg.class);

	// msg body vvv
	protected String url;
	protected String messageId;


	@Override
	public boolean addServerState(NettyServer server) {
		boolean serverState = server.pause.get();
		headers().set(MsgType.SERVER_STOP.getUrl(), String.valueOf(serverState));
		return serverState;
	}
	public boolean isServerHeaderStop() {
		return Boolean.parseBoolean(headers().getString(MsgType.SERVER_STOP.getUrl()));
	}

	public Msg setUrl(String url) { // auto detect Mgr
		setMgr(MsgType.fromUrl(url)); // if mgr type detected, url is set
		if(!isMgr()) this.url = url; // so check to set here
		return this;
	}
	public String getUrl() {
		return url;
	}
	public String getMessageId() {
		return messageId;
	}

	@Override
	public boolean isPing() {
		return super.isPing()
			|| MsgType.PING.getUrl().equals(messageId); // for 4.0.21.10 and below
	}

	public Msg setMessageId(String messageId) {
		this.messageId = messageId;
		return this;
	}
	public Msg reuse(String url) {
		setUrl(url);
		setMessageId(getMessageId());
		return this;
	}
	public Msg reuse(MsgType type) {
		setMgr(type);
		messageId = newMsgId();
		return this;
	}
	// msg body ^^^

	// msg header vvv
	protected final MsgHeader headers;
	@Override
	public void onHeader(String key, Handler<Object> applyer) {
		applyer.handle(headers().get(key));
	}
	public <T> void onHeaderT(String key, Handler<T> applyer) {
		T val = null;
		try {
			val = (T) headers().get(key);
		} catch (ClassCastException ignored){

		}
		applyer.handle(val);
	}

	public <T> void onHeaderNotNullT(String key, Handler<T> onHeaderVal) {
		T val = null;
		try {
			val = (T) headers().get(key);
		} catch (ClassCastException ignored){

		}
		if (val != null) {
			try {
				onHeaderVal.handle(val);
			} catch (ClassCastException ignored){
			}
		}
	}
	@Override
	public void onHeaderNotNull(String key, Handler<Object> onHeaderVal) {
		Object val = headers.get(key);
		if (val != null) {
			try {
				onHeaderVal.handle(val);
			} catch (ClassCastException ignored){
			}
		}
	}
	public void setHeaders(MsgHeader newHeader){
		if(newHeader != null) {
			headers.clear();
			headers.putAll(newHeader);
		}
	}

	@Override
	public void addHeaders(MsgHeader headersObj) {
		if(headersObj != null) headers.putAll(headersObj);
	}

	@Override
	public MsgHeader headers() {
		return headers;
	}

	@Override
	public ConcurrentHashMap<String, String> getStringHeaders() {
		return headers.getStringHeaders();
	}
	public boolean isCreate() {
		return isPing() && headers.contains(MsgHeader.MESH_HEADER) // for old till 4.2.0.3 ver
			|| MsgType.CREATE.equals(msgType); // old ver use create as messageId, url = ping // for all above ver separated CREATE with PING
	}
	@Override
	public ConcurrentHashMap<String, Object> getMapHeaders() { // no need to compose new map, cuz concurrentHM can create views
		return headers;
	}
	public boolean isServerPush() {
		return headers.isServerPush();
	}
	public Msg formatPush() {
		headers().set(MsgHeader.SERVER_PUSH, "0");
		return this;
	}
	public long setDecodeTime(long time) {
		headers.set(ChassisConst.CustomHeader.decodeTimeHeader, time); // accept obj
		return time;
	}

	@Override
	public long getDecodeTime() {
		Object val = headers.get(ChassisConst.CustomHeader.decodeTimeHeader);
		return val instanceof Long ? (Long) val : setDecodeTime(); // not suppose to receive from other peer
	}
	// msg header ^^^
	public void setMgr(MsgType type){
		super.setMgr(type);
		if (type != null) url = type.getUrl();
	}
	// constructors vvv
	public static Msg makeCreate(ByteBufAllocator bytebufAllocator) {
		Msg msg = makePing(bytebufAllocator);
		msg.headers.set(MsgHeader.MESH_HEADER, "?"); // set to ask
		return msg;
	}
	public static Msg makePing(ByteBufAllocator bytebufAllocator) {
		Msg msg = new Msg(bytebufAllocator);
		msg.setMgr(MsgType.PING);
		msg.setMessageId(MsgType.PING.getUrl());
		return msg;
	}

	public static Msg makeStop(ByteBufAllocator bytebufAllocator) {
		Msg msg = new Msg(bytebufAllocator);
		msg.setMgr(MsgType.SERVER_STOP);
		return msg;
	}

	public void copyAll(Msg origin) {
		super.copyAll(origin);
		addHeaders(origin.headers); // use add to not ref to origin.headers, it could be decompose later
		// getDecodeTime(); // deep clone header so no need recreate decodeTime
	}

	public Msg(Msg origin, boolean share) { // for clone
		super(origin, share);
		headers = share ? origin.headers : new MsgHeader(origin.headers);
		setUrl(origin.url);
		this.setMessageId(origin.messageId);
		getDecodeTime(); // to trigger add header decodeTime if not found
	}

	//// INBOUND MSG: msg with content buffer
	public Msg(ByteBuf content, String url, String messageId, MsgHeader refHeaders){
		// msg carrier with null header, url, msgId, decodeTime
		super(content); // for read only incoming msg
		headers = refHeaders != null ? refHeaders : new MsgHeader();
		setUrl(url);
		setMessageId(messageId);
		getDecodeTime(); // to trigger add header decodeTime if not found
	}
	public Msg(ByteBuf content, String url) { // simple msg without headers
		this(content, url, IDfy.generateNewId());
	}
	public Msg(ByteBuf content, String url, String messageId) { // simple msg without headers
		this(content, url, messageId, new MsgHeader());
		setDecodeTime(); // new header so set/overwrite new decodeTime
	}
	public Msg(ByteBuf content, MsgHeader headers) { // default gen msgId and url for later reuse
		this(content, "/", newMsgId(), headers);
	}


	//// OUTBOUND MSG: with empty buffer or from response data
	public Msg(ByteBufAllocator allocator, String url, String messageId, MsgHeader refHeaders){
		super(allocator);
		headers = refHeaders != null ? refHeaders : new MsgHeader();
		setUrl(url);
		setMessageId(messageId);
		getDecodeTime(); // to trigger add header decodeTime if not found
	}
	public Msg(ByteBufAllocator allocator, int initSize, String url, String messageId, MsgHeader refHeaders){
		super(allocator, initSize);
		headers = refHeaders != null ? refHeaders : new MsgHeader();
		setUrl(url);
		setMessageId(messageId);
		getDecodeTime(); // to trigger add header decodeTime if not found
	}
	public Msg(ByteBufAllocator allocator, String url, String messageId) { // simple msg without headers
		this(allocator, url, messageId, new MsgHeader());
		setDecodeTime(); // new header so set/overwrite new decodeTime
	}
	public Msg(ByteBufAllocator allocator, int initSize, String url, String messageId) { // simple msg without headers
		this(allocator, initSize, url, messageId, new MsgHeader());
		setDecodeTime(); // new header so set/overwrite new decodeTime
	}
	public Msg(ByteBufAllocator allocator, MsgHeader initHeaders) { // default gen msgId and url for later reuse
		this(allocator, "/", newMsgId(), initHeaders);
	}
	public Msg(ByteBufAllocator allocator, int initSize, MsgHeader initHeaders) { // default gen msgId and url for later reuse
		this(allocator, initSize, "/", newMsgId(), initHeaders);
	}
	public Msg(ByteBufAllocator allocator, String url) { // mgr Msg
		this(allocator, url, newMsgId());
	}
	public Msg(ByteBufAllocator allocator, int initSize, String url) { // mgr Msg
		this(allocator, initSize, url, newMsgId());
	}
	public Msg(ByteBufAllocator allocator){  // empty response msg
		this(allocator, new MsgHeader());
		setDecodeTime(); // new header so set/overwrite new decodeTime
	}
	public Msg(ByteBufAllocator allocator, int initSize){  // empty response msg
		this(allocator, initSize, new MsgHeader());
		setDecodeTime(); // new header so set/overwrite new decodeTime
	}
	@Override
	public Msg replicate() {
		Msg replicated = new Msg(this, false);
		replicated.writeFrom(this.getContent());
		return replicated;
	}

	
	@Override
	public Msg project() {
		return new Msg(this, true);
	}
	// constructor ^^^

	// destructor vvv
	// super
	// destructor ^^^

	// Blocked ops

	// Supported ops
	public String report() {
		return "Msg{url=" + url
			+ ", messageId=" + messageId
			+ ", headers=" + headers
			+ ", contentBrief=" + reportContentBrief()
			+ ", content=[" + contentHexString()
			+ "] }";
	}
	@Override @Deprecated
	public void validate() {
		super.validate();
		Objects.requireNonNull(url, "Message url is required");
		Objects.requireNonNull(messageId, "Message id is required");
	}
	@Override
	public String getProtoVersionText() {
		return ChassisConst.SupportVersion.TCP;
	}

	public void encodeTo(ByteBuf bb) {
		byte[] bytesUrl = url.getBytes(StandardCharsets.UTF_8);
		byte[] bytesMsgId = messageId != null ? messageId.getBytes(StandardCharsets.UTF_8) : new byte[]{};
		int oldContentLen = readableBytes();
		//16 byte: 4 byte mac dinh + 2 byte url + 4 byte message_id + 4 byte map header size
		int dataLength = 16 + oldContentLen + bytesUrl.length + bytesMsgId.length;
		AtomicInteger totalHeaderLen = new AtomicInteger(0);
		List<Pair<byte[], byte[]>> headerBytes = getStringHeaders().entrySet().stream().map(e -> {
			byte[] bKey = e.getKey().getBytes(StandardCharsets.UTF_8);
			byte[] bValue = e.getValue().getBytes(StandardCharsets.UTF_8);
			totalHeaderLen.addAndGet(8 + bKey.length + bValue.length);
			return Pair.of(bKey, bValue);
		}).collect(Collectors.toList());
		dataLength += totalHeaderLen.get();
		bb.ensureWritable(dataLength);
		bb.writeInt(dataLength - 4);
		bb.writeInt(bytesUrl.length);
		bb.writeBytes(bytesUrl);
		bb.writeInt(bytesMsgId.length);
		bb.writeBytes(bytesMsgId);
		bb.writeInt(headerBytes.size());

		if (totalHeaderLen.get() > 0) {
			headerBytes.forEach(pair->{ // just need to keep order of key-val
				bb.writeInt(pair.getKey().length);
				bb.writeBytes(pair.getKey());
				bb.writeInt(pair.getValue().length);
				bb.writeBytes(pair.getValue());
			});
		}
		dumpTo(bb);
		headerBytes.clear();
		if (logger.isDebugEnabled()) {
			logger.debug("Encode outbound result. url={}, msgID={}, " +
					"readableBytes={} must equal dataLen={} included: 16 + bytesUrl={} + bytesMsgId={} + " +
					"totalHeaderLen={}. bb.isReadable={}, hex={}", url, messageId
				, bb.readableBytes(), dataLength, bytesUrl.length
				, bytesMsgId.length, totalHeaderLen, bb.isReadable()
				, ChassisMgrableMsg.contentHexString(bb));
		}
	}

	public void setStatus(HttpResponseStatus statusCode) {
		if(statusCode!=null) headers().setStatus(statusCode);
	}
}
