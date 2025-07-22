package com.viettel.vocs.microchassis.http.client;

import com.viettel.vocs.microchassis.connection.client.ClientChannelAttribute;
import io.netty.channel.Channel;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.util.AttributeKey;

import java.util.concurrent.atomic.AtomicReference;

public interface HttpChannelAttribute extends ClientChannelAttribute { // share for both client and server

	AttributeKey<AtomicReference<Http2ConnectionEncoder>> http2encoder = AttributeKey.newInstance("Http2ConnectionEncoder");

	static Http2ConnectionEncoder getEncoder(Channel c) {
		AtomicReference<Http2ConnectionEncoder> encoderRef = getEncoderRef(c);
		return encoderRef != null ? encoderRef.get() : null; // might be null if channel died
	}

	static AtomicReference<Http2ConnectionEncoder> getEncoderRef(Channel c) {
		return c != null ? c.attr(http2encoder).get() : null;
	}
	static int streamIdConclude(Integer streamId, boolean isServer){
		int formatedStreamId = streamId == null ? 0 : streamId;
		return formatedStreamId > 0 ? formatedStreamId : (isServer ? 2 : 1);
	}
	static int getCurrentStreamID(Channel channel, boolean isServer) {
		Http2ConnectionEncoder encoder = HttpChannelAttribute.getEncoder(channel);
		return streamIdConclude(encoder == null ? null : encoder.connection().local().lastStreamCreated(), isServer);
	}
}
