package com.viettel.vocs.microchassis.http.codec;

import com.viettel.vocs.common.os.TimeUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.Http2Headers;

import java.util.Objects;

/**
 * @author tiennn18
 *
 * class for wrapping fullHttpResponse as a Msg based class, to pass to ctxHandler
 */
public class HttpResponse extends HttpMsg implements FullHttpResponse {
	protected HttpResponseStatus status = HttpResponseStatus.OK;
	public Http2Headers h2headers() {
		return HttpMsg.auditHttp2Headers(HttpMsg.http1HeadersToHttp2Headers(headers).add(mgrHeaders).status(status.codeAsText()));
	}

	// Override vvv
	@Override
	public HttpResponse copy() {
		return replicate();
	}

	@Override
	public HttpResponse duplicate() {
		return project();
	}

	@Override
	public HttpResponse touch() {
		chassisTouch();
		return this;
	}

	@Override
	public HttpResponse touch(Object hint) {
		chassisTouch(hint);
		return this;
	}
	public HttpResponse setProtocolVersion(HttpVersion version){
		protocolVersion = version;
		return this;
	}
	// Override ^^^

	public static FullHttpResponse toFullRetain(io.netty.handler.codec.http.HttpResponse res){
		/** After complete, Netty thread end -> HttpMessage release 1 -> need retain to keep body to other thread.
		 * 		the model is response came from eventloop, send trigger and handler callback came from user thread. Eventloop thread only handle response, not the callback -> need retain to end eventloop thread
		 * 	if empty body message DefaultHttpResponse dont have body -> no need to retain
		 */
		return ((FullHttpResponse) Objects.requireNonNull(res instanceof FullHttpResponse ? res : (res instanceof DefaultHttpResponse ? new DefaultFullHttpResponse(res.protocolVersion(), res.status()) : null))).retain();
	}
	/**
	 *
	 * @param msg will be DefaultFullHttpResponse created at HttpReplyContext.newOutMsg
	 */
	public HttpResponse(FullHttpResponse msg){ // for readonly manipulate // use with toFullRetain for manipulate readonly // client
		super(msg);
		copyExceptContent(msg);
	}
	public HttpResponse(ByteBufAllocator allocator, String msgId){
		this(allocator, DEFAULT_BUFFER_SIZE, msgId);
	}
	public HttpResponse(ByteBufAllocator allocator, int initContentSize, String msgId){
		this(allocator, initContentSize, msgId, HttpResponseStatus.OK);
	}
	public HttpResponse(ByteBufAllocator allocator, int initContentSize, String msgId, HttpResponseStatus status){ // base create response
		super(allocator, initContentSize); // create content
		DefaultFullHttpResponse newFakeMsg = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
		setMessageId(msgId, newFakeMsg.headers());
		copyExceptContent(newFakeMsg);
	}

	protected HttpResponse(HttpResponse origin, boolean shareBuffer) {
		super(origin, shareBuffer);
		copyAll(origin);
		if (shareBuffer) setDecodeTime(TimeUtils.nowNano());
	}

	public final void copyAll(HttpResponse origin) {
		super.copyAll(origin); // copy bytebuf content, protover, decodeResult
		setStatus(origin.status());
		setMessageId(origin.getMessageId());

	}
	public final void copyExceptContent(FullHttpResponse origin) {
		super.copyExceptContent(origin); // copy except bytebuf content
		setStatus(origin.status());
		setMessageId(getMessageId(origin.headers()));
	}
	@Override
	public HttpResponse replicate() {
		HttpResponse httpResponse = new HttpResponse(this, false);
		httpResponse.writeFrom(this.getContent());
		return httpResponse;
	}

	@Override
	public HttpResponse project() {
		return new HttpResponse(this, true);
	}



	@Override
	public String getUrl() {
		return "";
	}
	@Override
	public HttpResponse setUrl(String newUrl) {
		// only request has set url
		return this;
	}

	// Blocked ops
	@Override
	public final HttpResponse retainedDuplicate() { // redefine to block edit
		content().retainedDuplicate();
		return this;
	}
	@Override
	public final HttpResponse retain() {
		content().retain();
		return this;
	}
	@Override
	public final HttpResponse retain(int increment) {
		throw new UnsupportedOperationException(); // forbidden any kind of retain
	}
	public String report() {
		return String.format("HttpResponse{%s headers=%s, trailHeaders=%s, contentBrief=%s, messageId=%s, status=%s, content=[%s] }", getUrl(), headers, trailingHeaders, reportContentBrief(), getMessageId(), status(), contentHexString());
	}

	@Override
	public HttpResponse setHeader(CharSequence key, CharSequence value) {
		headers.set(key, value);
		return this;
	}

	@Override
	public HttpResponse addHeader(CharSequence key, CharSequence value) {
		headers.add(key, value);
		return this;
	}

	// RESPONSE SPECIFIC
	@Override @Deprecated
	public HttpResponseStatus getStatus() {
		return status();
	}

	@Override
	public HttpResponseStatus status() {
		return status;
	}

	@Override
	public HttpResponse setStatus(HttpResponseStatus status) {
		if(status!=null) this.status = status;
		return this;
	}
	@Override
	public HttpResponse replace(ByteBuf content) {
		replaceContent(content);
		return this;
	}
}
