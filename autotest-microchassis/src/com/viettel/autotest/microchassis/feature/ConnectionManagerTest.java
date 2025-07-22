package com.viettel.autotest.microchassis.feature;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.handler.tcp.EchoTcpHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.LogTcpHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.ConnectionConfiguration;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.tcp.client.TcpClient;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tcp.server.TcpServer;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;

import java.io.FileNotFoundException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author vttek
 */
public class ConnectionManagerTest {

    static final Logger logger = LogManager.getLogger(ConnectionManagerTest.class);
    private static TcpServer server;

    public ConnectionManagerTest() {
    }

    @BeforeAll
    public static void setUpClass(){
        LogUtils.setupLog4j2();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                server = new TcpServer(new ServerConfiguration("test-server", 9000));
                server.start(new EchoTcpHandler(server.getConfig()));
            } catch (Exception ex) {
                logger.error(ex, ex);
            }
        });
    }

    @AfterAll
    public static void tearDownClass() {
        try {
            ConnectionManager.getInstance().shutDown();
            server.stop();
        } catch (Exception e) {
            logger.error(e, e);
        }
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    // TODO add test methods here.
    // The methods must be annotated with annotation @Test. For example:
    //
    @Test
    void multipleClient() {
        byte[] bytes = "hello".getBytes();
        ConnectionConfiguration conf = new ConnectionConfiguration();
        conf.servers.add(new ServerConfiguration("pcf-tcp", 9000));
        conf.servers.add(new ServerConfiguration("pcf-http", 8000));
        conf.clients.add(new ClientConfiguration("localhost", 9000, "client_update")
                .setSendTimeoutMs(5000));
        conf.clients.add(new ClientConfiguration("localhost", 9000, "client_query")
                .setSendTimeoutMs(5000));
        try {
            AtomicBoolean asyncReceived = new AtomicBoolean();
            ConnectionManager.getInstance().setConfiguration(conf);
            TcpClient clientQuery = ConnectionManager.getInstance().getClient("client_query");
            TcpClient clientUpdate = ConnectionManager.getInstance().getClient("client_update");
            clientQuery.start(new LogTcpHandler(clientQuery.getConfig()));
            clientUpdate.start(new LogTcpHandler(clientQuery.getConfig(), arg0 -> asyncReceived.set(true)));
            TimeUtils.completeUnder(() -> clientQuery.isConnected() && clientUpdate.isConnected(), 10000);
            clientQuery.logConnectionStat();
            clientUpdate.logConnectionStat();
            Msg msg = clientQuery.sendSync(new Msg(Unpooled.wrappedBuffer(bytes), PayloadGenerator.testUrl, "x"));
            logger.info(msg);
            clientUpdate.send(new Msg(Unpooled.wrappedBuffer(bytes), PayloadGenerator.testUrl, "x"));
            TimeUtils.completeUnder(asyncReceived::get, 10000);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void testSplitRemote() {
        String a = "10.0.112.208/10.0.112.208:9002";
        assertEquals("10.0.112.208", a.split("/")[0]);
    }
}
