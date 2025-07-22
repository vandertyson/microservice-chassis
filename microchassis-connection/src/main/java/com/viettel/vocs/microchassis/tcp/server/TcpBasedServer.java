package com.viettel.vocs.microchassis.tcp.server;

import com.viettel.vocs.common.MathUtils;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpBasedHandler;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.connection.server.ServerChannelAttribute;
import com.viettel.vocs.microchassis.tcp.client.TcpPeer;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.group.ChannelMatcher;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author vttek
 * default with
 * - bidirectional Msg for Req and Res
 * - CCState for better monitor and usable with Backpressure if need *
 */


public abstract class TcpBasedServer<BiDMsg extends Msg, C extends ServerConfiguration, SHandler extends TcpBasedHandler, Initer extends TcpBasedServerChannelInitializer<BiDMsg, C, SHandler>>
	extends NettyServer<C, SHandler, Initer> implements TcpPeer<BiDMsg> {

	private static final Logger logger = LogManager.getLogger(TcpBasedServer.class);

	protected TcpBasedServer(C config) {
		super(config);
	}

	private void send(List<Channel> targets, BiDMsg pushMsg, boolean broadcast, GenericFutureListener<ChannelFuture>... futures) {
		if (broadcast) {
			try {
				targets.forEach(channel -> sendByChannelSharedBuffer(channel, pushMsg.replicate(), futures));
			} finally {
				pushMsg.decompose();
			}
		} else
			sendByChannelSharedBuffer(targets.get(MathUtils.randomNextInt(targets.size())), pushMsg.replicate(), futures); // khong can decompose, MsgEncoder will do, finally do decompose
	}

	public void send(BiDMsg message, boolean broadcastAllFound, GenericFutureListener<ChannelFuture>... futures) {
		try {
			List<Channel> connected = connectedChannels.stream().filter(f -> f.isActive() && f.isWritable()).collect(Collectors.toList());
			if (!connected.isEmpty()) send(connected, (BiDMsg) message.formatPush(), broadcastAllFound, futures);
		} catch (Exception ex) {
			logger.error(ex, ex);
		}
	}

	public void send(BiDMsg message, ChannelMatcher matcher, boolean broadcastAllFound, boolean forceSend, GenericFutureListener<ChannelFuture>... futures) {
		try {
			List<Channel> connected = connectedChannels.stream()
				.filter(channel -> channel.isActive() && channel.isWritable() && matcher.matches(channel))
				.collect(Collectors.toList());
			if (!connected.isEmpty()) {
				send(connected, (BiDMsg) message.formatPush(), broadcastAllFound, futures);
			} else if (forceSend) {
				logger.error("No connected channel for matcher. Random push");
				send(message, false, futures);
			}
		} catch (Exception ex) {
			logger.error(ex, ex);
		} finally {
			message.decompose();
		}
	}

	public void send(BiDMsg message, String clientVdu, boolean broadcastAllFound, boolean forceSend, GenericFutureListener<ChannelFuture>... futures) {
		try {
			List<Channel> connected = connectedChannels.stream()
				.filter(channel -> channel.isActive()
					&& channel.isWritable()
					&& Objects.equals(clientVdu, channel.attr(ServerChannelAttribute.attrClientVdu).get()))
				.collect(Collectors.toList());
			if (!connected.isEmpty()) {
				send(connected, (BiDMsg) message.formatPush(), broadcastAllFound, futures);
			} else if (forceSend) {
				logger.error("No connected channel for vdu {}. Random push", clientVdu);
				send(message, false, futures);
			}
		} catch (Exception ex) {
			logger.error(ex, ex);
		} finally {
			message.decompose();
		}
	}


	protected abstract BiDMsg buildStopMsg();

	public void notifyStop() {
		super.notifyStop();
		send(buildStopMsg(), true);
	}


	@Override
	public void stop() {
		notifyStop();
		super.stop();
	}
}
