package com.viettel.autotest.microchassis.feature;

import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.EchoTcpHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import com.viettel.vocs.microchassis.tcp.client.TcpClient;
import com.viettel.vocs.microchassis.tcp.server.TcpServer;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author tiennn18
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BasicFeatureTest {
	protected static final Logger logger = LogManager.getLogger(BasicFeatureTest.class);
	@Test
	@Order(8)
	void testPathVariableExtractor() {
		String userPattern1 = "/api/v1/tower/{towerID}/floor/{floorID}/room/{roomID}/{personID}";
		String userPattern2 = "/api/v1/tower/{towerID}/ab/floor/{floorID}/xyz/room/{roomID}/{personID}";
		String userPattern3 = "/api/v1/tower/{towerID}/{floorID}/{roomID}/{personID}";
		String userPattern4 = "/api/v1/room/{roomID}/persons";
		String userPattern5 = "/api/v1/persons/{personID}";
		String url1 = "/api/v1/tower/t1/floor/f1/room/r1/p1?query=name,abc,x";
		String url2 = "/api/v1/tower/t2/ab/floor/f2/xyz/room/r2/p2";
		String url3 = "/api/v1/tower/t3/f3/r3/p3";
		String url4 = "/api/v1/room/r4/persons";
		String url5 = "/api/v1/persons/p123445";
		String n_url1 = "/api/v1/tower/t1/floor/room/r1/p1?query=name,abc,x";
		String n_url2 = "/api/v1/tower/t1/floor/f1/f2/room/r1/p1?query=name,abc,x";
		try {
			logger.info(getPathVariable(userPattern1, url1));
			logger.info("===================================");
			logger.info(getPathVariable(userPattern2, url2));
			logger.info("===================================");
			logger.info(getPathVariable(userPattern3, url3));
			logger.info("===================================");
			logger.info(getPathVariable(userPattern4, url4));
			logger.info("===================================");
			logger.info(getPathVariable(userPattern5, url5));
			logger.info("===================================");
		} catch (Exception e) {
			logger.error(e);
			fail(e);
		}
		try {
			logger.info(getPathVariable(userPattern1, n_url1));
			logger.info("===================================");
		} catch (Exception e) {
			logger.error(e);
		}
		try {
			logger.info(getPathVariable(userPattern1, n_url2));
			logger.info("===================================");
		} catch (Exception e) {
			fail(e);
		}
	}

	public Map<String, String> getPathVariable(String userPattern, String url) throws Exception {
		Pattern p = Pattern.compile("\\{(\\w*)}");
		Matcher m = p.matcher(userPattern);
		Map<String, String> mapExtract = new HashMap<>();
		Map<String, Integer> subStringMap = new HashMap<>();
		while (m.find()) {
			String substring = userPattern.substring(0, m.start());
			int countMatches = StringUtils.countMatches(substring, "/");
			subStringMap.put(m.group(1), countMatches);
		}
		if (subStringMap.isEmpty()) {
			return null;
		}
		for (Map.Entry<String, Integer> entry : subStringMap.entrySet()) {
			Integer idx = entry.getValue();
			String name = "(/\\w*)({" + idx + "})";
			Pattern p2 = Pattern.compile(name);
			Matcher m2 = p2.matcher(url);
			if (m2.find()) {
				String x = m2.group(1);
				mapExtract.put(entry.getKey(), x.substring(1));
			} else {
				throw new Exception("Cannot find path variable for paramName: \"" + entry.getKey() + "\"");
			}
		}
		return mapExtract;
	}
	@Test
	void shutdown() {
		try {
			HttpServer server = new HttpServer(new HttpServerOptions("test", 9000));
			server.start(new HttpServerHandler(server.getConfig()));
			if(!TimeUtils.completeUnder(server.ch::isActive, 3000)) Assertions.fail("Can not start server");
			server.stop();
		} catch (Exception e) {
			logger.error(e, e);
			fail(e);
		}
	}

	@BeforeAll
	public void setUpClass(){
		LogUtils.setupLog4j2();
	}



	@Test
	void notfyStop() {
		try {
			TcpServer server = new TcpServer(new ServerConfiguration("test", 6367));
			server.start(new EchoTcpHandler(server.getConfig()));
			if(!TimeUtils.completeUnder(server.ch::isActive, 3000)) Assertions.fail("Can not start server");
			TcpClient client1 = new TcpClient(new ClientConfiguration("localhost", 6367, "connected-client"));
			client1.start(new TcpHandler(client1.getConfig()) {
				@Override
				public void handle(TcpContext tctx) {
					logger.info("HA1");
				}

				@Override
				public void timeoutHandle(String requestID) {
					logger.info("TO1");
				}
			});
			String content = "hello";
			assertEquals(new String(client1.sendSync(client1.createReq(content.getBytes(StandardCharsets.UTF_8), PayloadGenerator.testUrl)).getContent(), StandardCharsets.UTF_8), content);
			server.notifyStop();
			TcpClient client2 = new TcpClient(new ClientConfiguration("localhost", 6367, "not-connected-client"));
			client2.start(new TcpHandler(client2.getConfig()) {
				@Override
				public void handle(TcpContext tctx) {
					logger.info("HA2");
				}

				@Override
				public void timeoutHandle(String requestID) {
					logger.info("TO2");
				}
			});
			TimeUtils.waitSafeMili(10000);
			assertEquals(false, client2.isConnected());
			server.stop();

		} catch (Exception e) {
			logger.error(e, e);
			fail(e);
		}
	}

}
