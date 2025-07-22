package com.viettel.autotest.microchassis.lib.depricatedNeedRefactor;

import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import io.netty.channel.Channel;

public interface BenchmarkClient {

    Channel sendAsync(byte[] data, String requestID) throws Exception;

//    public abstract long getCurrentResponseCount();

    void init(String host, int port, AsyncClientHandler handler) throws Exception;

    void send(byte[] data, String valueOf) throws Exception;

    ChassisMgrableMsg sendSync(byte[] data, String requestID) throws Exception;

    void setCallBack(Handler<Object> callBackSend);

}
