package com.viettel.vocs.microchassis.http.codec;

import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.HttpConversionUtil;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;

public abstract class HttpMsg extends ChassisMgrableMsg<HttpHeaders, String, String> implements FullHttpMessage{
	public static String getMessageId(HttpHeaders headers) {
		return headers.get(ChassisConst.CustomHeader.msgIdHeader); // allow null
	}
//	public static String getMessageId(Http2Headers headers) {
//		return headers.get(ChassisConst.CustomHeader.msgIdHeader).toString();
//	}

	public static void setMessageId(String messageId, HttpHeaders headers) {
		if(messageId != null) headers.set(ChassisConst.CustomHeader.msgIdHeader, messageId); // only set on main header
		else headers.remove(ChassisConst.CustomHeader.msgIdHeader); // remove if null
	}
//	public static void setMessageId(String messageId, Http2Headers headers) {
//		headers.set(ChassisConst.CustomHeader.msgIdHeader, Objects.requireNonNull(messageId)); // only set on main header
//	}
	public static Http2Headers auditHttp2Headers(Http2Headers h2headers) {
		h2headers.remove(CONNECTION);
//		List<CharSequence> removeKeys = new ArrayList<>();
//		h2headers.forEach(entry -> {
//			if (entry.getKey().toString().contains("_")) {
//				removeKeys.add(entry.getKey());
//			}
//		});
//		removeKeys.forEach(h2headers::remove);
		return h2headers;
	}

	@Override
	public boolean addServerState(NettyServer server) {
		boolean serverState = server.pause.get();
		headers().set(MsgType.SERVER_STOP.getUrl(), String.valueOf(serverState));
		return serverState;
	}
	public boolean isServerHeaderStop() {
		return Boolean.parseBoolean(headers().get(MsgType.SERVER_STOP.getUrl()));
	}
	public boolean isHttp2() {
		return streamId > 0;
	}
	public static boolean isHttp2(FullHttpMessage fullMsg) {
		return getStreamId(fullMsg) > 0; // client 1 server 2
	}
	public static int getStreamId(FullHttpMessage req) {
		try {
			return Integer.parseInt(req.headers().get(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text()));
		} catch (Exception e) {
			return 0; // HTTP1 bcuz no streamId
		}
	}


	DecoderResult decoderResult = DecoderResult.SUCCESS;
	protected HttpHeaders headers = new DefaultHttpHeaders();
	protected final Http2Headers mgrHeaders = new DefaultHttp2Headers();
	protected HttpHeaders trailingHeaders = new DefaultHttpHeaders();
	@Getter protected int streamId; // 0 (null) if client no use 0, server from 2, client from 1

	public HttpHeaders trailingHeaders() {
		return trailingHeaders;
	}

	@Override
	public void onHeader(String key, Handler<String> applyer) {
		String val = headers.get(key);
		if (val == null) val = trailingHeaders.get(key);
		applyer.handle(val);
	}

	public void onHeader(HttpConversionUtil.ExtensionHeaderNames key, Handler<String> applyer) {
		onHeader(key.text().toString(), applyer);
	}

	public void onHeader(Http2Headers.PseudoHeaderName key, Handler<String> applyer) {
		onHeader(key.value().toString(), applyer);
	}

	public void onHeaderNotNull(HttpConversionUtil.ExtensionHeaderNames key, Handler<String> onHeaderVal) {
		onHeaderNotNull(key.text().toString(), onHeaderVal);
	}

	public void onHeaderNotNull(Http2Headers.PseudoHeaderName key, Handler<String> onHeaderVal) {
		onHeaderNotNull(key.value().toString(), onHeaderVal);
	}

	@Override
	public void onHeaderNotNull(String key, Handler<String> onHeaderVal) {
		String val = headers.get(key);
		if (val == null) val = trailingHeaders.get(key);
		if (val != null) {
			try {
				onHeaderVal.handle(val);
			} catch (ClassCastException ignored) {
			}
		}
	}


	@Override
	public ConcurrentHashMap<String, String> getStringHeaders() {
		ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();
		headers.forEach(header -> result.put(header.getKey(), header.getValue()));
		trailingHeaders.forEach(header -> result.put(header.getKey(), header.getValue()));
		return result;
	}

	@Override
	public String getMessageId() {
		String messageId = getMessageId(headers);
		return messageId != null ? messageId : ""; // only set on main header
	}


	@Override
	public HttpMsg setMessageId(String messageId) {
		setMessageId(messageId, headers);
		return this;
	}


	@Override
	public HttpHeaders headers() {
		return headers;
	}

	public long setDecodeTime(long time) {
		headers.set(ChassisConst.CustomHeader.decodeTimeHeader, String.valueOf(time));
		return time;
	}

	@Override
	public long getDecodeTime() {
		String decodeTime = headers.get(ChassisConst.CustomHeader.decodeTimeHeader);
//		if (decodeTime == null) decodeTime = trailingHeaders.get("decodeTime"); // only set on main header
		return decodeTime == null
			? setDecodeTime()
			: Long.parseLong(decodeTime);
	}

	public void addHeaders(Http2Headers h2h) {
		if (h2h != null) {
			mgrHeaders.add(h2h);
			/**
			 * doi voi HTTP2, msgId duoc nhan tu Http2Headers, nen neu trong Http2Headers co mess_id, thi ghi de vao HttpHeaders cho tuong duong
			 * getMessageId luon lay tu HttpHeaders, nen se ghi xuong Http1 headers, tren Http2Headers co cung duoc xoa di cung duoc
			 * 		code included below
			 * if(mgrHeaders.contains(ChassisConst.CustomHeader.msgIdHeader))
			 * 				headers.set(ChassisConst.CustomHeader.msgIdHeader, mgrHeaders.get(ChassisConst.CustomHeader.msgIdHeader));
			 * asked
			 * is that true that all http 2 standard header start with ":"
			 * ChatGPT said:
			 * Yes, that's correct — in HTTP/2, pseudo-headers are special headers that start with a colon (:). They are defined by the HTTP/2 specification and are used to carry essential request or response information that was part of the start-line in HTTP/1.1 (e.g., GET /index.html HTTP/1.1).
			 */
			mgrHeaders.forEach(entry-> {
				if (!entry.getKey().toString().contains(":"))
					headers.set(entry.getKey(), entry.getValue());
			});
			// for HTTP2 server onHeaderRead
			if(!mgrHeaders.contains(ChassisConst.CustomHeader.msgIdHeader)) headers.remove(ChassisConst.CustomHeader.msgIdHeader);
		}
	}
	@Override
	public void addHeaders(HttpHeaders headersObj) {
		if (headersObj != null) {
			if (this.headers == null) {
				this.headers = headersObj;
			} else {
				this.headers.add(headersObj);
			}
		}
	}
	abstract HttpMsg setHeader(CharSequence key, CharSequence value);
	abstract HttpMsg addHeader(CharSequence key, CharSequence value);
	@Override
	public void setHeaders(HttpHeaders headersObj) {
		if (headersObj != null) headers.set(headersObj);
	}

	public void addTrailingHeaders(HttpHeaders headersObj) {
		if (headersObj != null) trailingHeaders.add(headersObj);
	}

	public void setTrailingHeaders(HttpHeaders headersObj) {
		if (headersObj != null) trailingHeaders.set(headersObj);
	}

// public void setHeaders // dont have bcuz DefaultReq/Res has final headers field




	// msg header ^^^

	protected HttpVersion protocolVersion = HttpVersion.HTTP_1_1;


	@Deprecated
	public DecoderResult getDecoderResult() {
		return decoderResult();
	}

	public DecoderResult decoderResult() {
		return decoderResult;
	}

	public void setDecoderResult(DecoderResult result) {
		decoderResult = Objects.requireNonNull(result);
	}

	protected HttpMsg(ByteBufAllocator allocator, int initSize) {
		super(allocator, initSize);
		// not yet finish, need copyFrom of child
	}

	protected HttpMsg(ByteBuf buf) {
		super(buf);
		// not yet finish, need copyFrom of child
	}

	protected HttpMsg(ChassisMgrableMsg<HttpHeaders, String, String> origin, boolean shareBuffer) {
		super(origin, shareBuffer);
		// not yet finish, need copyFrom of child
	}
	protected HttpMsg(FullHttpMessage msg) {
		this(msg instanceof ChassisMgrableMsg ? ((ChassisMgrableMsg) msg).getReadOnlyContentByteBuf() : msg.content()); // for manipulating content
		// not yet finish, need copyFrom of child
	}

	public static Http2Headers http1HeadersToHttp2Headers(FullHttpMessage originMsg) {
		Pair<Http2Headers, Http2Headers> headersPair = http1HeadersToHttp2HeadersPair(originMsg);
		Http2Headers http2Headers = headersPair.getLeft();
		http2Headers.add(headersPair.getRight()); // merge trailing into main header
		return http2Headers;
	}
	public static Http2Headers http1HeadersToHttp2Headers(HttpHeaders headers) {
		Http2Headers http2Headers = new DefaultHttp2Headers();
		// read n add standard headers: METHOD PATH SCHEME AUTHORITY
//		Arrays.stream(Http2Headers.PseudoHeaderName.values()).map(Http2Headers.PseudoHeaderName::value).forEach(headerKey -> {
//			String value = headers.get(headerKey);
//			if (value != null) http2Headers.set(headerKey, value);
//		});
//		Arrays.stream(HttpConversionUtil.ExtensionHeaderNames.values()).map(HttpConversionUtil.ExtensionHeaderNames::text).forEach(headerKey -> {
//			String value = headers.get(headerKey);
//			if (value != null) http2Headers.set(headerKey, value);
//		});
		headers.forEach(entry -> http2Headers.set(entry.getKey(), entry.getValue()));
//		http2Headers.method(originMsg.method().name()); // method is already set in pseudo header
//		http2Headers.path(originMsg.uri()); // path is already set in pseudo header
//		boolean isSsl = originMsg instanceof io.netty.handler.codec.http.HttpRequest && ((io.netty.handler.codec.http.HttpRequest) originMsg).scheme().equalsIgnoreCase("https");
		//		http2Headers.scheme(isSsl ? HttpScheme.HTTPS.name() : HttpScheme.HTTP.name());
		return http2Headers;
	}
	public static Pair<Http2Headers, Http2Headers /*trailing*/> http1HeadersToHttp2HeadersPair(FullHttpMessage originMsg) {
		HttpHeaders headers = originMsg.headers();
		HttpHeaders trailingHeaders = originMsg.trailingHeaders();

		Http2Headers http2Headers = http1HeadersToHttp2Headers(headers);
		Http2Headers trail2Headers = http1HeadersToHttp2Headers(trailingHeaders);
		// read n add ext headers
		String value;
		if ((value = headers.get(HttpHeaderNames.HOST)) != null) http2Headers.authority(value);
		if(originMsg instanceof io.netty.handler.codec.http.HttpRequest) {
			io.netty.handler.codec.http.HttpRequest request = (io.netty.handler.codec.http.HttpRequest) originMsg;
			if (request.method() != null && http2Headers.method() == null && trail2Headers.method() == null) http2Headers.method(request.method().asciiName());
			if (request.uri() != null && http2Headers.path() == null && trail2Headers.path() == null) http2Headers.path(request.uri());
		} else if(originMsg instanceof io.netty.handler.codec.http.HttpResponse){
			io.netty.handler.codec.http.HttpResponse response = (io.netty.handler.codec.http.HttpResponse) originMsg;
			if (response.status() != null && http2Headers.status() == null && trail2Headers.status() == null) http2Headers.status(response.status().codeAsText());
		}
		return Pair.of(http2Headers, trail2Headers);
	}

	public HttpVersion protocolVersion() {
		return protocolVersion;
	}

	@Override
	public ByteBuf content() {
		return super.content();
	}

	public abstract HttpMsg setProtocolVersion(HttpVersion version);

	protected void copyAll(HttpMsg originMsg) {
		super.copyAll((ChassisMgrableMsg<HttpHeaders, String, String>) originMsg);
		setDecoderResult(originMsg.decoderResult());
		setProtocolVersion(originMsg.protocolVersion());
		headers.set(originMsg.headers());
		trailingHeaders.set(originMsg.trailingHeaders());
	}
	protected final void copyExceptContent(FullHttpMessage originMsg) {
		// buffer is copied by constructor, 
		headers.set(originMsg.headers());
		trailingHeaders.set(originMsg.trailingHeaders());
		streamId = getStreamId(originMsg);
		setProtocolVersion(originMsg.protocolVersion());
	}
	@Override
	public String getProtoVersionText() {
		return protocolVersion().text();
	}

	public HttpVersion getProtocolVersion() {
		return protocolVersion();
	}

	// Supported ops
	public boolean release() {
		content().release();
		return true;
	}

	public boolean release(int decrement) {
		return content().release(decrement);
	}
}
