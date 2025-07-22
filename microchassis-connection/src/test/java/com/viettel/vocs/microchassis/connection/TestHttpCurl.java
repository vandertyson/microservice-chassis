package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tiennn18
 * to test, open and call curl
 */
public class TestHttpCurl {
	static HttpServer h1server; // default 1.1
	static final String testContent = "testr0n20r202u0x0pfncfyp0hfp0ym02cp0hfp0[0fyk2-90ypx3h0fh0x";
	static final AtomicBoolean isTested = new AtomicBoolean(false);

	@Test
	void main(String[] args) {
		try {
			h1server = new HttpServerOptions("h1server").newServer();
			h1server.start(new HttpServerHandler(h1server.getConfig()) {
				@Override
				public void getHandle(HttpServerContext serverCtx) {
					try {
						System.out.println("echo server handle get");
						serverCtx.send(testContent.getBytes(StandardCharsets.UTF_8));
					} catch (Exception e) {
						logger.error(e, e);
					}
				}

				@Override
				public void postHandle(HttpServerContext serverCtx) {
					System.out.println("echo server handle post " + serverCtx.getInData().length);
					try {
						serverCtx.send(serverCtx.getInData());
					} catch (Exception e) {
						logger.error(e, e);
					}
				}

				@Override
				public void putHandle(HttpServerContext serverCtx) {
					System.out.println("echo server handle put " + serverCtx.getInData().length);
					try {
						serverCtx.send(serverCtx.getInData());
					} catch (Exception e) {
						logger.error(e, e);
					}
				}

				@Override
				public void deleteHandle(HttpServerContext serverCtx) {
					System.out.println("REceive shutdown req");
					isTested.set(true);
					h1server.stop();
				}
			});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		while(TimeUtils.waitSafeMili(5000) && !isTested.get()) {
			System.out.println("Testing ...");
		}
	}
}
