package com.viettel.vocs.microchassis.depricated;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

public interface ChassisHttpRequest extends FullHttpRequest  {
	HttpMethod method();

	ChassisHttpRequest setMethod(HttpMethod method);
	String getUrl();
	String report();
	void setUrl(String newUri);
	ChassisHttpRequest setHeader(CharSequence key, CharSequence value);
	ChassisHttpRequest addHeader(CharSequence key, CharSequence value);
}
