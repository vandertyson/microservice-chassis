package com.viettel.vocs.microchassis.codec.handler.http;

import com.viettel.vocs.microchassis.codec.handler.MessageBasedHandler;
import com.viettel.vocs.microchassis.http.codec.HttpMsg;

public interface HttpMessageHandler<HttpMessage extends HttpMsg> extends MessageBasedHandler<HttpMessage> {
}
