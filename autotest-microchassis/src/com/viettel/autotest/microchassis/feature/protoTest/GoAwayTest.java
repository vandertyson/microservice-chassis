package com.viettel.autotest.microchassis.feature.protoTest;

import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.connection.config.SslConfiguration;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.http.client.HttpChannelAttribute;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.client.HttpClientConnection;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static io.netty.handler.codec.http.HttpMethod.POST;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author tiennn18
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GoAwayTest {
	@BeforeAll
	public void setUpClass(){
		LogUtils.setupLog4j2();
	}

	protected static final Logger logger = LogManager.getLogger(GoAwayTest.class);

	static String asyncPayload = "Async payload";

	public static void main(String[] args) {
		try {
			System.out.println("STart MAIN");
			startServer(9013).waitServerClose();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	private static HttpServer startServer(int port) throws Exception {
		HttpServerOptions options = new HttpServerOptions("testH2s", port);
		HttpServer server = new HttpServer(options);
		server.start(new HttpServerHandler(options));
		if (!TimeUtils.completeUnder(server.ch::isActive, 3000))
			Assertions.fail("Can not start server");
		return server;
	}

	@Test
	void shutdown() {
		try {
//			startServer(9000);
			// microchassis ko expose connection ra client1 de su dung -> chi co the gui nhan binh thuong,
			// khi ko co nhu cau, phuc vu duy nhat case shutdown clear task con ton trong queue thi can GOAWAY

			HttpServerOptions options = new HttpServerOptions("testH2s", 9000);
			HttpServer server = new HttpServer(options);
			server.start(new HttpServerHandler(options) {
				@Override
				public void handle(HttpServerContext ctx) {
					super.handle(ctx);
					assertEquals(asyncPayload, ctx.getInMsg().toString(StandardCharsets.UTF_8));
					logger.info("reply after sleep 1s");
				}
			});
			if (!TimeUtils.completeUnder(server.ch::isActive, 3000)) Assertions.fail("Can not start server");
			HttpClientOptions config = new HttpClientOptions("localhost", 9000, "testH2c1");
			HttpClient client1 = new HttpClient(config);
			client1.start(new HttpClientHandler(config));
			// connected 1 conn to server
			assertTrue(client1.isConnected());
			assertEquals(1, client1.countConnected());
			// send 100 msg -> streamId = 1+200 = 201
			int sendReplay = 1;
			int streamIncreased = sendReplay * 2 + 1;

			for (int i = 0; i < sendReplay; i++)
				client1.send(client1.createReq(POST, PayloadGenerator.testUrl, asyncPayload.getBytes()), UUID.randomUUID().toString()); // send but not yet response after 1s
			TimeUtils.waitSafeMili(500);
			assertEquals(streamIncreased, HttpChannelAttribute.getEncoder(client1.getConnections().toArray(Routable[]::new)[0].getChannel()).connection().local().lastStreamCreated());
			assertEquals(1, server.countConnected()); // van la 1 conn
			TimeUtils.waitSafeMili(100);
			server.notifyStop(); // to stop client 1 sendable // not yet response
			TimeUtils.waitSafeMili(500);
//			assertTrue(ChannelAttribute.isGoAwaySet(client1.getConnections().toArray(Routable[]::new)[0].nettyChannel));
			assertEquals(1, server.countConnected()); // sau khi stop van con channel
			assertEquals(streamIncreased, HttpChannelAttribute.getEncoder(client1.getConnections().toArray(Routable[]::new)[0].getChannel()).connection().local().lastStreamCreated());
			System.out.println("EXPECTED DIE");
			for (int i = 0; i < 1; i++) {
				client1.send(client1.createReq(POST, PayloadGenerator.testUrl, asyncPayload.getBytes()), UUID.randomUUID().toString());
			}
			// sau khi send tiep van la 201 va cac msg failed
			TimeUtils.waitSafeMili(32000);
			assertTrue(((HttpClientConnection) client1.getConnections().toArray(Routable[]::new)[0]).isGoAway());
			TimeUtils.waitSafeMili(200000);
			assertEquals(201, HttpChannelAttribute.getEncoder(client1.getConnections().toArray(Routable[]::new)[0].getChannel()).connection().local().lastStreamCreated());
			// send ok HTTP2 msg

			HttpResponse response2 = client1.sendSync(client1.createReq(POST, PayloadGenerator.testUrl, asyncPayload.getBytes()));
			// send on previous connection, must failed receive GOAWAY
			assertEquals(asyncPayload, response2.content().toString(StandardCharsets.UTF_8));
			HttpClient client2 = new HttpClient(new HttpClientOptions("localhost", 9000, "testH2c2"));
			client2.start(new HttpClientHandler(config)); // connect new must success
			TimeUtils.waitSafeMili(1000); // vi event handler cua server la async nen phai cho thi moi thay client2 conn

			assertTrue(client1.isConnected());
			assertTrue(client2.isConnected());
			assertTrue(client1.getConnections().toArray(Routable[]::new)[0] instanceof HttpClientConnection);
			assertTrue(client2.getConnections().toArray(Routable[]::new)[0] instanceof HttpClientConnection);
			assertTrue(client1.getConnections().toArray(Routable[]::new)[0].isConnected());
			assertTrue(client2.getConnections().toArray(Routable[]::new)[0].isConnected());

			HttpResponse response1_2 = client2.sendSync(client2.createReq(POST, PayloadGenerator.testUrl, asyncPayload.getBytes()));
			assertEquals(asyncPayload, response1_2.content().toString(StandardCharsets.UTF_8)); // conn 2 must send ok

			client1.close();
			client2.close();
			assertFalse(client1.isConnected());
			assertFalse(client2.isConnected());
			server.stop();
		} catch (Exception e) {
			logger.error(e, e);
			fail(e);
		}
	}

	@Test
	void pingTest() {
		try {
			int port = 9393;
//			startServer(9000);
			// microchassis ko expose connection ra client1 de su dung -> chi co the gui nhan binh thuong,
			// khi ko co nhu cau, phuc vu duy nhat case shutdown clear task con ton trong queue thi can GOAWAY

			HttpServer hs = new HttpServer(new HttpServerOptions("testH2s", port));
			hs.start(new HttpServerHandler(hs.getConfig()) {
				@Override
				public void handle(HttpServerContext ctx) {
					super.handle(ctx);
					assertEquals(asyncPayload, ctx.getInMsg().toString(StandardCharsets.UTF_8));
					logger.info("reply after sleep 1s");
				}
			});
			if (!TimeUtils.completeUnder(() -> hs.ch != null && hs.ch.isActive(), 3000))
				Assertions.fail("Can not start server");
			HttpClientOptions clientOptions = new HttpClientOptions("localhost", port, "testH2c1");
//			clientOptions.http2Configuration.setIsUpgradeHttp2(true);
			HttpClient h2c = new HttpClient(clientOptions);
			logger.info("H2 config {}", h2c.getConfig().http2);
			h2c.start(new HttpClientHandler(clientOptions));
			// connected 1 conn to server
			TimeUtils.waitSafeMili(3000);
			assertTrue(h2c.isConnected());
			assertEquals(1, h2c.countConnected());
			assertEquals(asyncPayload, h2c.sendSync(h2c.createReq(POST, PayloadGenerator.testUrl, asyncPayload.getBytes()), UUID.randomUUID().toString()).content().toString(StandardCharsets.UTF_8)); // send but not yet response after 1s
			assertEquals(1, hs.countConnected()); // van la 1 conn
			// send 100 msg -> streamId = 1+200 = 201
			while (true) {
				assertEquals(asyncPayload, h2c.sendSync(h2c.createReq(POST, PayloadGenerator.testUrl, asyncPayload.getBytes()), UUID.randomUUID().toString()).content().toString(StandardCharsets.UTF_8)); // send but not yet response after 1s
				assertEquals(1, hs.countConnected()); // van la 1 conn
				assertFalse(((HttpClientConnection) h2c.getConnections().toArray(Routable[]::new)[0]).isGoAway());
				assertTrue(h2c.getConnections().toArray(Routable[]::new)[0] instanceof HttpClientConnection);
			}
		} catch (Exception e) {
			logger.error(e, e);
			fail(e);
		}
	}

	@Test
	void sslTest() {
		try {
			int port = 9139;
//			startServer(9000);
			// microchassis ko expose connection ra client1 de su dung -> chi co the gui nhan binh thuong,
			// khi ko co nhu cau, phuc vu duy nhat case shutdown clear task con ton trong queue thi can GOAWAY

			HttpServer hs = new HttpServer(
				new HttpServerOptions("testH2s", port)
					.setSslConfiguration(new SslConfiguration()
//						.setJksPath("/home/vht/Projecs/javawork/microservice-chassis-1m/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/minions/echo-server.jks")
//						.setPassPhrase("your_passphrase")
							.setCertPath("/home/vht/Projecs/javawork/microservice-chassis-1m/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/minions/echo-server.crt")
							.setKeyPath("/home/vht/Projecs/javawork/microservice-chassis-1m/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/minions/echo-pkcs8.key")
							.setTrustPath("/home/vht/Projecs/javawork/microservice-chassis-1m/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/minions/fw1-server.crt")
//						.setRequireClientAuth(true)
					));
			hs.start(new HttpServerHandler(hs.getConfig()) {
				@Override
				public void handle(HttpServerContext ctx) {
					super.handle(ctx);
					assertEquals(asyncPayload, ctx.getInMsg().toString(StandardCharsets.UTF_8));
					logger.info("reply after sleep 1s");
				}
			});
			try {
				TimeUtils.waitUntil(() -> hs.ch != null && hs.ch.isActive(), 3000);
			} catch (TimeoutException e) {
				Assertions.fail("Can not start server");
			}
			HttpClientOptions clientOptions = new HttpClientOptions("localhost", port, "testH2c1")
				.setSslConfiguration(new SslConfiguration()
//					.setJksPath("/home/vht/Projecs/javawork/microservice-chassis-1m/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/minions/fw1-server.jks")
//					.setPassPhrase("your_passphrase")
//					.setCertPath("/home/vht/Projecs/javawork/microservice-chassis-1m/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/minions/fw1-server.crt")
//					.setKeyPath("/home/vht/Projecs/javawork/microservice-chassis-1m/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/minions/fw1-pkcs8.key")
						.setTrustPath("/home/vht/Projecs/javawork/microservice-chassis-1m/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/minions/echo-server.crt")
//					.setRequireClientAuth(true)
				);
			clientOptions.http2.setIsUpgradeHttp2(true);
			HttpClient h2c = new HttpClient(clientOptions);
			logger.info("H2 config {}", h2c.getConfig().http2);
			h2c.start(new HttpClientHandler(clientOptions));
			// connected 1 conn to server
			assertTrue(h2c.isConnected());
			assertEquals(1, h2c.countConnected());
			assertEquals(1, hs.countConnected()); // van la 1 conn
			// send 100 msg -> streamId = 1+200 = 201
			int sendReplay = 2;
			int streamIncreased = sendReplay * 2 + 1;
			for (int i = 0; i < sendReplay; i++)
				assertEquals(asyncPayload, h2c.sendSync(h2c.createReq(POST, PayloadGenerator.testUrl, asyncPayload.getBytes()), UUID.randomUUID().toString()).content().toString(StandardCharsets.UTF_8)); // send but not yet response after 1s
//			Thread.sleep(500);
			Routable theOnlyConnH2 = h2c.getConnections().toArray(Routable[]::new)[0];
			assertEquals(streamIncreased, HttpChannelAttribute.getEncoder(theOnlyConnH2.getChannel()).connection().local().lastStreamCreated());
			assertEquals(1, hs.countConnected()); // van la 1 conn
//			Thread.sleep(100);
			hs.notifyStop(); // to stop client 1 sendable // not yet response
			TimeUtils.waitSafeMili(1000);
//			assertTrue(ChannelAttribute.isGoAwaySet(client1.getConnections().toArray(Routable[]::new)[0].nettyChannel));
//			assertEquals(1, hs.countConnected()); // sau khi stop van con channel
//			assertEquals(streamIncreased, h2c.getConnections().toArray(Routable[]::new)[0].nettyChannel.attr(Http2ClientChannelAttribute.attrEncoder).get().connection().local().lastStreamCreated());
			assertTrue(((HttpClientConnection) theOnlyConnH2).isGoAway());
			System.out.println("EXPECTED DIE");
			assertTrue(theOnlyConnH2 instanceof HttpClientConnection);
			assertTrue(theOnlyConnH2.isConnected());
			h2c.close();
			assertFalse(h2c.isConnected());
			hs.stop();
		} catch (Exception e) {
			logger.error(e, e);
			fail(e);
		}
	}
}
