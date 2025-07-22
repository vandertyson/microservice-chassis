package com.viettel.vocs.microchassis.connection.client;

import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.exception.ConnectionException;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;

/**
 * @author vttek
 */


public interface ChannelMonitorable {
	ClientConfiguration getConfig();

	void onChannelRenew(Channel newChannel);

	void onChannelDown();

	String getHostName();

	Integer getPort();

	void connect(int retry) throws ConnectionException;

	boolean isClosed();

	Channel getChannel();

	boolean ping(ByteBufAllocator allocator);


	String getMonitorID();

}
