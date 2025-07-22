/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.server;

import com.viettel.vocs.common.ocs.ApplicationID;
import com.viettel.vocs.microchassis.connection.server.ServerChannelAttribute;
import io.netty.channel.Channel;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.util.AttributeKey;

/**
 *
 * @author vttek
 */
public class HttpServerChannelAttribute extends ServerChannelAttribute {
    public static final AttributeKey<ApplicationID> attrChannelAppID = AttributeKey.newInstance("server.ApplicationID");

    public static final AttributeKey<Long> attrCloseTime = AttributeKey.newInstance("server.ChannelAttrCloseTime");
	public static boolean isGoAwaySet(Channel channel) {
			if (channel != null) {
					String h2hName = String.valueOf(Http2Connection.class);
					if (channel.pipeline().get(h2hName) != null) {
							Http2Connection connection = (Http2Connection) channel.pipeline().get(h2hName);
							return connection.goAwayReceived() || connection.goAwaySent();
					}
			}
			return false;
	}
}
