package com.viettel.autotest.microchassis.compatible;

import com.viettel.autotest.microchassis.lib.depricatedNeedRefactor.AsyncClientHandler;
import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.autotest.microchassis.lib.depricatedNeedRefactor.BenchmarkClient;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.tcp.client.TcpClient;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.jctools.queues.MpmcArrayQueue;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class TcpBenchmarkClient implements BenchmarkClient {
	private TcpClient tcpClient;
	public MpmcArrayQueue<TcpContext> queue = new MpmcArrayQueue<>(1000);
	public AtomicLong countCreate = new AtomicLong();
	Handler<Object> callBackSend;

	@Override
	public Channel sendAsync(byte[] data, String requestID) throws Exception {
		return null;
	}


	@Override
	public void init(String host, int port, AsyncClientHandler handler) throws Exception {
		ClientConfiguration config = new ClientConfiguration(host, port, "id");
		tcpClient = new TcpClient(config);
		tcpClient.start(new TcpHandler(config){
		});
		while (!tcpClient.isConnected()) {
			LockSupport.parkNanos(1000);
		}
	}

	@Override
	public void send(byte[] data, String requestID) throws Exception {
		tcpClient.send(new Msg(Unpooled.wrappedBuffer(data), PayloadGenerator.testUrl, requestID));
	}

	@Override
	public Msg sendSync(byte[] data, String requestID) throws Exception {
		Msg msg = new Msg(Unpooled.wrappedBuffer(data), PayloadGenerator.testUrl, requestID);
		return tcpClient.sendSync(msg);
	}

	@Override
	public void setCallBack(Handler<Object> callBackSend) {
		this.callBackSend = callBackSend;
	}


}
