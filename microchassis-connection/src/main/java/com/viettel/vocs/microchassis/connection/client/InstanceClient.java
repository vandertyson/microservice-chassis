package com.viettel.vocs.microchassis.connection.client;

import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.handler.ClientHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.dns.HostNameResolver;
import com.viettel.vocs.microchassis.connection.exception.ClientException;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.MainSite;
import io.netty.channel.Channel;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * This class create instance use client from config without updater, run in single instance most basic mode
 *
 * @param <Req>
 * @param <Res>
 * @param <Conf>
 */
public abstract class InstanceClient<
	Req extends ChassisMgrableMsg,
	Res extends ChassisMgrableMsg,
	Conf extends ClientConfiguration,
	CHandler extends ClientHandler>
	extends MainSite<Req, Res, Conf, CHandler> {
	protected static final Logger logger = LogManager.getLogger(InstanceClient.class);

	//	protected final EventHandler eventHandler = new NoEventHandler();
	protected static void checkAndCloseConn(ClientConnection conn) {
		if (conn != null) {
			conn.graceFulDeregister();
			if (logger.isDebugEnabled())
				logger.debug("Close connection {}:{} after no-use when 0CC ", conn.getId(), conn.getChannelString());
		}
	}

	protected static void checkAndCloseConn(Channel c) {
		// only check at handle has context, else drop or timeout need retry so keep it at that, any succsses handle will trigger close,
		if (c != null) checkAndCloseConn((ClientConnection) ClientChannelAttribute.getConnection(c));
	}

	protected boolean isAllowNewConnection(Endpoint e) {
		return true;
	}

	protected InstanceClient(Conf config, CHandler handler) {
		super(config);
		this.mainSite = this;
		this.handler = Objects.requireNonNull(handler, "Cannot set null CtxHandler for NettyClient"); // handler must be provided to all resources
		nettyInitialized.fuse(true);
	}

	protected ClientConnection<Req, Res, Conf, CHandler> reuseDirectConnection(Endpoint endpoint) {
		// search trong site hostname
		return (ClientConnection<Req, Res, Conf, CHandler>) getOpenConnections().get(endpoint.toString());
	}

	private ClientConnection<Req, Res, Conf, CHandler> createDirectCustomHandlerConnection(Endpoint endpoint) {
		// checkInit(); da check init trong ham goi ham nay
		try {
			if (logger.isDebugEnabled())
				logger.debug("[Create temporary connection]{id={}, host={}, port={}}", config.id, endpoint.ip, endpoint.port);
			return createConnection(this, endpoint, handler, eventHandler, false);
		} catch (Exception e) {
			logger.error(e, e);
		}
		return null;
	}

	private String getRandomIpFromResolve(String host) throws ClientException {
		List<String> ips = new ArrayList<>(HostNameResolver.doResolveDNS(host));
		if (ips.isEmpty()) {
			throw new ClientException("Can not resolve hostname " + host);
		} else {
			return ips.get(ThreadLocalRandom.current().nextInt(0, ips.size()));
		}
	}


	public Map<Endpoint, Res> sendSyncBroadcast(String host, int port, Req request) throws Exception {
		return sendSyncBroadcast(host, port, request, config.sendTimeoutMs);
	}
	public Map<Endpoint, Res> sendSyncBroadcast(Endpoint endpoint, Req request) throws Exception {
		return sendSyncBroadcast(endpoint, request, config.sendTimeoutMs);
	}
	public Map<Endpoint, Res> sendSyncBroadcast(Endpoint endpoint, Req request, long timeoutMs) throws Exception {
		if (endpoint.ip == null)
			return sendSyncBroadcast(endpoint.serviceName, endpoint.port, request, timeoutMs);
		else return sendSyncBroadcast(endpoint.ip, endpoint.port, request, timeoutMs);
	}

	public Res sendSync(Endpoint endpoint, Req request) throws Exception {
		return sendSync(endpoint, request, config.sendTimeoutMs);
	}

	public Res sendSync(String host, int port, Req request) throws Exception {
		return sendSync(host, port, request, config.sendTimeoutMs);
	}

	public void send(String host, int port, Req request) throws Exception {
		// checkInit(); da check init trong ham duoi
		String ip;
		try {
			ip = getRandomIpFromResolve(host);
		} catch (ClientException resolveEx) {
			request.decompose();
			throw resolveEx;
		}
		sendDirect(Endpoint.newEndpoint(host, ip, port), request);
	}

	public Res sendSync(Endpoint endpoint, Req request, long timeoutMs) throws Exception {
		if (endpoint.ip == null)
			return sendSync(endpoint.serviceName, endpoint.port, request, timeoutMs);
		else return sendSync(endpoint.ip, endpoint.port, request, timeoutMs);
	}

	public Res sendSync(String host, int port, Req request, long timeoutMs) throws Exception {
		// checkInit(); da check init trong ham duoi
		String ip;
		try {
			ip = getRandomIpFromResolve(host);
		} catch (ClientException resolveEx) {
			request.decompose();
			throw resolveEx;
		}
		return sendSyncDirect(Endpoint.newEndpoint(host, ip, port), request, timeoutMs);
	}

	public Map<Endpoint, Res> sendSyncBroadcast(String hostOrIp, int port, Req request, long timeoutMs) throws Exception {
		// checkInit(); da check init trong ham getConn
		Endpoint representEndpoint = Endpoint.newEndpoint(hostOrIp, port);
		if (representEndpoint.ip == null) { // this is a dns endpoint
			Set<String> ipSet = HostNameResolver.doResolveDNS(representEndpoint.serviceName);
			if (ipSet.isEmpty())
				throw new ClientException("Empty hostname resolve for broadcast" + representEndpoint.serviceName);
			try {
				return makeBroadcastJobs(ipSet, request).entrySet().stream().parallel().map(job -> {
					String ip = job.getValue().getValue().get(0);
					Endpoint directE = new Endpoint(hostOrIp, ip, port);
					Res res = null;
					try {
						res = sendSyncDirect(directE, job.getValue().getKey(), timeoutMs);
					} catch (Exception e) {
						logger.error("Broadcast to endpoint " + directE + " failed", e);
					}
					return Pair.of(directE, res);
				}).collect(Collectors.toMap(Pair::getKey, Pair::getValue, (a,b)->a));
			} finally {
				request.decompose();
			}
		} else // endpoint can use directly as single point
			return Map.of(representEndpoint, sendSyncDirect(representEndpoint, request, timeoutMs)); // khong can decompose, MsgEncoder will do
	}

	private ClientConnection<Req, Res, Conf, CHandler> getDirectConn(Endpoint endpoint) {
		checkInit();
		ClientConnection<Req, Res, Conf, CHandler> conn;
		return ((conn = reuseDirectConnection(endpoint)) != null && conn.isSendable())
			||
			((conn = createDirectCustomHandlerConnection(endpoint)) != null && conn.isSendable())
			? conn : null;
	}

	// private sync send with timeout
	private Res sendSyncDirect(Endpoint endpoint, Req request, long timeoutMs) throws Exception {
		// TODO dinh nghia xu ly cho params, hien tai chua co yeu cau su dung
		ClientConnection<Req, Res, Conf, CHandler> conn = getDirectConn(endpoint);
		if (conn != null) {
			try {
				Res res = conn.sendSync(request, timeoutMs);
				checkAndCloseConn(conn);
				return res;
			} catch (Exception ex) {
				if (!(ex instanceof TimeoutException)) {
					request.decompose();
				}
				throw ex;
			}
		} else {
			request.decompose();
			throw new ClientException("Can not create connection to " + endpoint);
		}
	}

	// private sync send with custom handler
	private void sendDirect(Endpoint endpoint, Req request) throws Exception {
		ClientConnection<Req, Res, Conf, CHandler> conn = getDirectConn(endpoint);
		if (conn != null) {
			try {
				conn.send(request);
			} catch (Exception ex) {
				if (!(ex instanceof TimeoutException)) {
					request.decompose();
				}
				throw ex;
			}
		} else {
			request.decompose();
			throw new ClientException("Can not create connection to " + endpoint);
		}
	}
}
