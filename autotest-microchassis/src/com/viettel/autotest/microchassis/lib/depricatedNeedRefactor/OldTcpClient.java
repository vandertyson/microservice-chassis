package com.viettel.autotest.microchassis.lib.depricatedNeedRefactor;

import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.tcp.client.TcpClient;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.jctools.queues.MpmcArrayQueue;

import java.util.concurrent.atomic.AtomicLong;

public class OldTcpClient implements BenchmarkClient {

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
        tcpClient = new TcpClient(new ClientConfiguration(host, port, "id"));
        tcpClient.start(new TcpHandler(tcpClient.getConfig()) {

        });
    }

    @Override
    public void send(byte[] data, String requestID) throws Exception {
        tcpClient.send(new Msg(Unpooled.wrappedBuffer(data), PayloadGenerator.testUrl, requestID));
    }

    @Override
    public HttpResponse sendSync(byte[] data, String requestID) throws Exception {
        throw new Exception("Not supported yet");
    }

    @Override
    public void setCallBack(Handler<Object> callBackSend) {
        this.callBackSend = callBackSend;
    }

}
