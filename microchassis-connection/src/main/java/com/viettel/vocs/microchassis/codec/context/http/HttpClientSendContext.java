//package com.viettel.vocs.microchassis.codec.context.http;
//
//import com.viettel.vocs.microchassis.codec.context.ChassisNoReplyNoContext;
//import com.viettel.vocs.microchassis.codec.context.ClientSendContext;
//import com.viettel.vocs.microchassis.http.codec.Http1Request;
//import io.netty.handler.codec.http.FullHttpRequest;
//import io.netty.handler.codec.http.HttpMethod;
// no use, can use request to replace this
//public class HttpClientSendContext extends ChassisNoReplyNoContext<Http1Request> implements ClientSendContext<Http1Request>, HttpContext {
//	protected HttpClientSendContext(Http1Request inChargedMsg) {
//		super(inChargedMsg);
//	}
//	public HttpMethod getRequestMethod() {
//		return inChargedMsg.method();
//	}
//
//	public HttpClientSendContext(FullHttpRequest in) {
//		this(new Http1Request(in));
//	}
//
//	@Override
//	public boolean isHttp2() {
//		return ;
//	}
//}
