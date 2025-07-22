package com.viettel.vocs.microchassis.base;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.datatype.StringUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 * static value class for codecs
 */
public class Endpoint {
	public static final String LOCALHOST = "localhost";
	public static final String LOCALHOST_IP = "127.0.0.1";
	
	public String ip;
	
	public int port;
	public static long countEndpoint(Collection<Endpoint> list, Endpoint target){
		return list.stream().filter(e -> Objects.equals(e, target)).count();
	}

	public String serviceName;
	public String getTarget(){
		return ip != null ? ip : serviceName;
	}
	transient public String serverPodId;

	transient public String clientPodId;
	transient public int serverCount = -2; // -2 havent asked , -1 impolite other = real serverCount

	//	public Endpoint() {} // for Msg to jsoniter.deserialize
	public static Endpoint clone(Endpoint dest) {
		return new Endpoint(dest.serviceName, dest.ip, dest.port);
	}

	public Endpoint(String serviceName, String ipString, int port) {
		this.ip = ipString;
		this.port = port;
		this.serviceName = serviceName;
	}

	protected Endpoint(String ipString, int port) {
		this(null, ipString, port);
	}

	public static Set<Endpoint> listListenIp(int port) {
		Set<Endpoint> bindIfs = new CopyOnWriteArraySet<>();
		try {
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			while (networkInterfaces.hasMoreElements()) {
				NetworkInterface networkInterface = networkInterfaces.nextElement();
				if (networkInterface.isUp()) {
					Enumeration<InetAddress> interfaceAddresses = networkInterface.getInetAddresses();
					while (interfaceAddresses.hasMoreElements()) {
						InetAddress address = interfaceAddresses.nextElement();
						if (address instanceof Inet4Address) {
							// Check if the address is bound to the specified port
							try (Socket socket = new Socket()) {
								socket.bind(new InetSocketAddress(address, port));
								bindIfs.add(Endpoint.newEndpoint(address.getHostAddress(), port)); // called at server
							} catch (IOException e) {
								// Address is not bound to the port
							}
						}
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
		return bindIfs;
	}

	public static Endpoint newEndpoint(String hostOrIp, int port) {
		return isIPv4(hostOrIp)
			? (hostOrIp.equals(LOCALHOST)
			? Endpoint.newEndpoint(LOCALHOST, LOCALHOST, port)
			: Endpoint.newEndpoint(hostOrIp.equals(LOCALHOST_IP) ? LOCALHOST : null, hostOrIp.equals(LOCALHOST_IP) ? LOCALHOST : hostOrIp, port))
			: Endpoint.newEndpoint(hostOrIp, null, port);
	}

	public static Endpoint newEndpoint(String connectionString) {
		List<Endpoint> endpoints = newEndpoints(connectionString);
		if (endpoints.size() >= 1) return endpoints.get(0);
		else return null;
	}

	public static List<Endpoint> newEndpoints(String connectionString) {
		List<Endpoint> results = Arrays.stream(StringUtils.parseDelimiterString(connectionString))
			.map(s -> {
				String[] split = s.split(":");
				if (split.length == 2) {
					return Endpoint.newEndpoint(split[0], Integer.parseInt(split[1]));
				} else return null;
			}).filter(Objects::nonNull).collect(Collectors.toList());
		if (results.size() >= 1) return results;
		else return new ArrayList<>();
	}

	public static Endpoint newEndpoint(String serviceName, String ipString, int port) {
		Endpoint e = new Endpoint(serviceName, ipString, port);
		e.serverPodId = CommonConfig.InstanceInfo.INSTANCE_ID.get(); // this pod is client or server
		return e;
	}

	public static Endpoint remote(ChannelHandlerContext ctx) {
		return Endpoint.remote(ctx.channel());
	}
	public static Endpoint local(ChannelHandlerContext ctx) {
		return Endpoint.local(ctx.channel());
	}

	public static Endpoint remote(Channel channel) {
		return readPeer(channel.remoteAddress());
	}

	public static Endpoint local(Channel channel) {
		return readPeer(channel.localAddress());
	}
	@Nullable
	private static Endpoint readPeer(SocketAddress channel) {
		if (channel instanceof InetSocketAddress) {
			InetSocketAddress inetSocketAddress = (InetSocketAddress) channel;
			return Endpoint.newEndpoint(inetSocketAddress.getHostString(), inetSocketAddress.getPort());
		} else if (channel == null) return null;
		/**
		 * ke ca client ket noi bang bootstrap.connect("localhost", port) thi server se doc ra 127.0.0.1, match Ipv4
		 * neu 0.0.0.0 thi drop tai client os
		 * neu broadcast se drop tai client os (TCP/IP), chi chap nhan 255....255 voi 1 so giao thuc nhu DNS, ...
		 * SocketAddress remoteAddress = channel.remoteAddress(); // maybe null
		 * 	neu connection bi close do loi bat ky cua os thi remoteAddress() se tra ve null, day la hoat dong read unsafe
		 */
		else return null;
	}


	public static boolean equalsWithoutDns(Endpoint e1, Endpoint e2) {
		return Objects.equals(e1.ip,e2.ip) && e1.port == e2.port;
	}

	@Override
	public boolean equals(Object e) {
		if (e instanceof Endpoint) {
			return Objects.equals(((Endpoint) e).serviceName, serviceName)
				&& Objects.equals(((Endpoint) e).ip, ip)
				&& ((Endpoint) e).port == port;
		} else return false;
	}

	

	@Override
	public String toString() {
		return serviceName == null
			? ip + ":" + port
			: (
			ip == null
				? serviceName + ":" + port
				: serviceName + "@" + ip + ":" + port
		);
	}

	public String toFormalString() {
		return String.format("%s:%d", serviceName != null ? serviceName : (ip != null ? ip : CommonConfig.InstanceInfo.HOST_IP.get()), port);
	}
	public static boolean isIPv4(String ipAddress) { // included "localhost" treat as Ipv4 by nature resolved to 127.0.0.1
		if (ipAddress != null) {
			try {
				InetAddress inetAddress = InetAddress.getByName(ipAddress);
				return inetAddress instanceof java.net.Inet4Address;
			} catch (UnknownHostException ignored) {
			}
		}
		return false;
	}

	public long convertIpToNumber() {
		try {
			byte[] bytes = InetAddress.getByName(ip).getAddress();
			long number = 0;

			for (byte b : bytes) {
				number <<= 8;
				number |= (b & 0xFF);
			}

			return number;
		} catch (UnknownHostException e) {
			return -1;
		}
	}

	public static String parseIP(String remote) {
		try {
//            10.0.112.208/10.0.112.208:9002
			return remote.split("/")[0];
		} catch (Exception ex) {
			return null;
		}
	}

	public void setUnknownServerPodId() {
		clientPodId = null;
	}

	public boolean isUnknownServerPodId() {
		return clientPodId == null;
	}

	public void setUnknownClientPodId() {
		clientPodId = null;
	}

	public boolean isUnknownClientPodId() {
		return clientPodId == null;
	}

	public void switchRoleClientServer() {
		String tmp = clientPodId;
		clientPodId = serverPodId;
		serverPodId = tmp;
	}

	transient protected boolean askClaimed = false;
	transient protected boolean connectClaimed = false;

	public synchronized Endpoint failedToConnect() {
		askClaimed = false; // for re-ask
		connectClaimed = false;
		return this;
	}

	public synchronized boolean claimToConnect() {
		if (!connectClaimed) {
			connectClaimed = true;
			return true;
		} else return false;
	}

	public synchronized boolean claimToAsk() {
		if (!askClaimed) {
			askClaimed = true;
			return true;
		} else return false;
	}

	public int getServerCount() {
		return serverCount;
	}

	public void setServerCount(int serverCount) {
		this.serverCount = serverCount;
	}

	public boolean isReadyToConnect() {
		return serverCount >= 0 // connectable by server
			&& !connectClaimed; // connectable by not claimed
	}

	public Endpoint patchServiceName(String serviceName) {
		if (this.serviceName == null && (
			serviceName.equals(LOCALHOST) || !isIPv4(serviceName))
		) this.serviceName = serviceName;
		return this;
	}

	public String status() {
		return String.format("[%s%s/%d]%s", askClaimed ? "a" : "_", connectClaimed ? "c" : "_", serverCount, this);
	}
}
