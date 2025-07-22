package com.viettel.vocs.microchassis.http.codec;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.metrics.InternalMetric;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.Http2Headers;

import java.util.Objects;

/**
 * @author tiennn18
 */
public class HttpRequest extends HttpMsg implements FullHttpRequest {
	protected HttpMethod method = HttpMethod.GET;
	protected String uri = "/";

	// override vvv
	public HttpRequest setProtocolVersion(HttpVersion version){
		protocolVersion = Objects.requireNonNull(version);
		return this;
	}

	// override ^^^
	public String report() {
		return String.format("HttpReq{%s %s headers=%s, contentBrief=%s, messageId=%s, content=[%s] }", method(), getUrl(), headers(),  reportContentBrief(),getMessageId(),  contentHexString());
	}

	public final void copyAll(HttpRequest origin) {
		super.copyAll(origin); // copy as Http1Msg
		setUri(origin.uri());
		setMethod(origin.method());
		streamId = origin.streamId;
	}
	public final void copyExceptContent(FullHttpRequest origin) {
		super.copyExceptContent(origin); // copy except bytebuf content
		setUri(origin.uri());
		setMethod(origin.method());
		streamId = getStreamId(origin);
		setMessageId(getMessageId(origin.headers()));
	}
	public HttpRequest(FullHttpRequest msg){ // for all server or client wrap DefaultFullHttpRequest/Generator
		super(msg);
		copyExceptContent(msg);
	}
	public HttpRequest(ByteBufAllocator allocator, HttpMethod method, String path, int streamId) { // for server2, which must have streamId
		this(allocator, DEFAULT_BUFFER_SIZE, method, path);
		this.streamId = streamId;
	}
	public HttpRequest(ByteBufAllocator allocator, HttpMethod method, String path) { // for client
		this(allocator, DEFAULT_BUFFER_SIZE, method, path);
	}
	public HttpRequest(ByteBufAllocator allocator, int initContentSize, HttpMethod method, String path) { // for client // base create request
		super(allocator, initContentSize); // create content
		DefaultFullHttpRequest newFakeMsg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path);
		setMessageId(newMsgId(), newFakeMsg.headers());
		copyExceptContent(newFakeMsg);
		if(HttpVersion.HTTP_1_1.equals(newFakeMsg.protocolVersion())) headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		// else // set SO_KEEPALIVE on NettyServer bootstrap
	}
	public HttpRequest(ByteBufAllocator allocator, HttpMethod method, String path, byte[] content) {
		// for craft request, havent sent so no stream id
		this(allocator, Integer.max(DEFAULT_BUFFER_SIZE, content.length), method, path);
		writeFrom(content);
	}
	public void addData(ByteBuf data) {
		writeNext(data);
		NettyServer.serverMetric.add(InternalMetric.Server.TOTAL_BYTES_RECEIVE, data.readableBytes());
	}
	public HttpRequest(HttpRequest origin, boolean shareBuffer){
		super(origin, shareBuffer);
		copyAll(origin);
		if (shareBuffer) setDecodeTime(TimeUtils.nowNano());
	}

	@Override
	public HttpRequest setHeader(CharSequence key, CharSequence value) {
		headers.set(key, value);
		return this;
	}

	@Override
	public HttpRequest addHeader(CharSequence key, CharSequence value) {
		headers.add(key, value);
		return this;
	}
	@Override
	public HttpRequest replicate() {
		HttpRequest httpRequest = new HttpRequest(this, false);
		httpRequest.writeFrom(this.getContent());
		return httpRequest;
	}

	@Override
	public HttpRequest project() {
		return new HttpRequest(this, true);
	}
	public static String getUrl(Http2Headers headers) {
		return headers.path().toString();
	}

	public static void setUrl(Http2Headers headers, String newUri) {
		headers.path(Objects.requireNonNull(newUri));
	}
	@Override
	public String getUrl() {
		return uri();
	}
	@Override
	public HttpRequest setUrl(String newUri) {
		return setUri(newUri);
	}

	@Override
	public HttpRequest copy() {
		return replicate();
	}

	@Override
	public HttpRequest duplicate() {
		return project();
	}

	@Override
	public HttpRequest touch() {
		chassisTouch();
		return this;
	}

	@Override
	public HttpRequest touch(Object hint) {
		chassisTouch(hint);
		return this;
	}
	@Override
	public final HttpRequest retainedDuplicate() { // redefine to block edit
		content().retainedDuplicate();
		return this;
	}

	@Override
	public HttpRequest retain(int increment) {
		content().retain(increment);
		return this;
	}

	@Override
	public HttpRequest retain() {
		content().retain();
		return this;
	}



	// REQUEST SPECIFIC
	@Override @Deprecated
	public HttpMethod getMethod() {
		return method();
	}
	public static HttpMethod method(Http2Headers headers) {
		return HttpMethod.valueOf(headers.method().toString());
	}
	@Override
	public HttpMethod method() {
		return method;
	}
	@Override
	public HttpRequest setMethod(HttpMethod method) {
		this.method = Objects.requireNonNull(method);
		return this;
	}

	@Override @Deprecated
	public String getUri() {
		return uri();
	}
	@Override
	public HttpRequest setUri(String uri) {
		this.uri=Objects.requireNonNull(uri);
		return this;
	}

	@Override
	public String uri() {
		return uri;
	}
	@Override
	public HttpRequest replace(ByteBuf content) {
		replaceContent(content);
		return this;
	}
}
