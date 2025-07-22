package com.viettel.vocs.microchassis.depricated;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

public interface ChassisHttpResponse extends FullHttpResponse {
	HttpResponseStatus status();

	ChassisHttpResponse setStatus(HttpResponseStatus status);
	String report();
	ChassisHttpResponse setHeader(CharSequence key, CharSequence value);
	ChassisHttpResponse addHeader(CharSequence key, CharSequence value);
}
