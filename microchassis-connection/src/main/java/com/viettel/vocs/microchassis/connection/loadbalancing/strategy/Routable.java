package com.viettel.vocs.microchassis.connection.loadbalancing.strategy;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.connection.client.ChannelMonitorable;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ClientLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import com.viettel.vocs.microchassis.tcp.client.TcpClientChannelAttribute;
import com.viettel.vocs.microchassis.tcp.codec.TCIDecoder;
import io.netty.channel.Channel;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public interface Routable extends ChannelMonitorable, EndpointHolder {
	boolean isConnected();
	void waitFlushAllMsg();
	boolean isInDnsResolve();
	static boolean isConnected(Channel c){
		return c != null
//			&& nettyChannel.isOpen() // included in isActive
//			&& nettyChannel.isWritable() // khong duoc dung isWritable, vi trang thai writable thay doi nhanh theo duong truyen, isConnected dung de check state cho strategy va interval reclassify
			// trang thai writable la trang thai tam thoi tren 1 duong truyen van dang ket noi, dang open, se co api isSendable() de check cu the hon
			&& c.isActive();
	}
	boolean isSendable();
	long getSentCC(boolean recent); // current sending in flight
	boolean isEnable();
	String getChannelString();
	boolean isWritable();
	void processTimeoutMsgId(Channel sendChannel, String msgId);

	/**
	 * completely remove from site
	 */
	void deregister();

	boolean checkAvailability();
	void close();
	ConnectionMode skipWarmUp();
	default ConnectionMode overwriteMode(ConnectionMode newMode){
		TCIDecoder decoder = TcpClientChannelAttribute.getDecoder(getChannel());
		if (newMode != null && decoder != null) decoder.setMode(newMode);
		return newMode;
	}
	ConnectionMode checkMode();
	int warmingUpTPS();
	PeerCounter getCounter();
	ClientLimiter getLimiter();
	String getId();
	int getPeerLoad();
	String ip();
	boolean tryAccquire();
	void acquire(TimeUtils.NanoBeacon startBeacon) throws TimeoutException;
	boolean checkUsable();
	void setEnable(boolean b);
	AtomicReference<RouteStrategy.CONNECTION_STATUS> getState();
	ConnectionMode getConnMode();

	void triggerRegister();

	void setDeleteMarker(boolean b);
	boolean isDeleteMarker();
	boolean isDisableRoute();

	default void graceFulDeregister(){
		setDeleteMarker(true);
		waitFlushAllMsg();
		deregister(); // check to keep conn if any else thread wait sending
	}



//	void applyNewStrategy(); // after set new strategy to routeStrategyRef, channel cannot detect this change, call apply to propagate changes
}
