package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.common.log.LogUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;

import java.io.FileNotFoundException;

public class UrlParamTest  {
	protected static final Logger logger = LogManager.getLogger(UrlParamTest.class);

	@BeforeAll
	public void setUpClass(){
		LogUtils.setupLog4j2();
	}
	private static final Integer PORT = 9319;

//	private class FloorHandler implements RestHandler<Http1ServerContext> {
//		@Override
//		public void deleteHandle(Http1ServerContext context) {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.get().send("delete");
//		}
//
//		@Override
//		public void getHandle(Http1ServerContext context) {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.get().send("get");
//		}
//
//		@Override
//		public void postHandle(Http1ServerContext context) {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.get().send("post");
//		}
//
//		@Override
//		public void putHandle(Http1ServerContext context) {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.get().send("put");
//		}
//
//		@Override
//		public void patchHandle(Http1ServerContext context) {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.get().send("patch");
//		}
//
//		@Override
//		public void notSupportMethodHandle(Http1ServerContext context) {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.get().send("notSupport");
//		}
//
//		@Override
//		public void serverErrorHandle(Http1ServerContext context, Exception ex) {
//			Map<String, List<String>> data = context.getInParams();
//			logger.info(data);
//			context.send("errorOccurred");
//		}
//
//		@Override
//		public void defaultHandle(Http1ServerContext context) {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.send("default");
//		}
//	}

//	@Test
//	public void test() throws Exception {
//		HttpServer server = new HttpServer(new HttpServerOptions("test", PORT));
//		UrlPatternHandler handler = new UrlPatternHandler(server.getConfig());
//
//		//region define specific method and pattern handler
//		String userPattern1 = "/api/v1/tower/{towerID}/floor/{floorID}/room/{roomID}/{personID}";
//		handler.registerPattern(POST, userPattern1, context -> {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.send("user1");
//		});
//
//		String userPattern2 = "/api/v1/tower/{towerID}/{floorID}/{roomID}/{personID}";
//		handler.registerPattern(POST, userPattern2, context -> {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.send("user2");
//		});
//
//		String roomPattern1 = "/api/v1/room/{roomID}/persons";
//		handler.registerPattern(POST, roomPattern1, context -> {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.send("room");
//		});
//
//		String personsPattern1 = "/api/v1/persons/{personID}";
//		handler.registerPattern(POST, personsPattern1, context -> {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.send("person");
//		});
//
//		String personsPattern3 = "/api/v1/persons";
//		handler.registerPattern(POST, personsPattern3, context -> {
//			Map<String, String> data = context.getUrlParams();
//			logger.info(data);
//			context.send("person3");
//		});
//
//		handler.registerUndefinedHandler(context -> {
//			try {
//				//match pattern nhung khong du tham so
//				UrlPatternHandler.RejectedCtxHolder rctx = context;
//				if (rctx.getMissingParam() != null && rctx.getMatchedPattern() != null) {
//					context.get().send("url pattern " + rctx.getMatchedPattern() + " miss param " + rctx.getMissingParam(), HttpResponseStatus.BAD_REQUEST);
//				} else {
//					//khong match pattern nao
//					context.get().send(HttpResponseStatus.NOT_FOUND);
//				}
//			} catch (Exception ex) {
//				logger.error(ex, ex);
//			}
//		});
//		//endregion
//
//
//		//region define combined handler
//		String floorPt1 = "/api/v1/floor/{floorID}";
//		FloorHandler towerHandler = new FloorHandler();
//		handler.registerPattern(floorPt1, towerHandler);
//
//		//endregion
//		server.start(handler);
//
//
//		//some testUrl
//		Map<String, Integer> tests = new HashMap<>();
//		tests.put("/api/v1/tower/t1/floor/f1/room/r1/p1?query=name,abc,x", 200);
//		tests.put("/api/v1/tower/t2/ab/floor/f2/xyz/room/r2/p2", 404);
//		tests.put("/api/v1/tower/t3/f3/r3/p3", 200);
//		tests.put("/api/v1/room/r4/persons", 200);
//		tests.put("/api/v1/persons/p123445", 200);
//		tests.put("/api/v1/tower/t1/floor/room/r1/p1?query=name,abc,x", 404);
//		tests.put("/api/v1/tower/t1/floor/f1/f2/room/r1/p1?query=name,abc,x", 404);
//		tests.put("/api/v1/persons", 200);
//
//
//		List<Triple<String, HttpMethod, Integer>> testCombine = new ArrayList<>();
//		testCombine.add(new ImmutableTriple<>("/api/v1/floor/f1", POST, 200));
//		testCombine.add(new ImmutableTriple<>("/api/v1/floor/f1", POST, 200));
//		testCombine.add(new ImmutableTriple<>("/api/v1/floor/f1", GET, 200));
//		testCombine.add(new ImmutableTriple<>("/api/v1/floor", POST, 200));
//		testCombine.add(new ImmutableTriple<>("/api/v1/floor", PUT, 200));
//		testCombine.add(new ImmutableTriple<>("/api/v1/floor/f1/person", PUT, 404));
//
//
//		//create test client
//		HttpClientOptions options = new HttpClientOptions("localhost", PORT, "test");
//		options.setSendTimeoutMs(600_000);
//		Http1Client client = new Http1Client(options);
//		for (Map.Entry<String, Integer> entry : tests.entrySet()) {
//			logger.info("================{}================", entry.getKey());
//			Http1Request rq = client.createReq(POST, entry.getKey());
//			try {
//				FullHttpResponse rs = client.sendSync(rq);
//				logger.info("Response. status={}, body={}", rs.status(), rs.content().toString(CharsetUtil.UTF_8));
//				assertEquals(entry.getValue(), rs.status().code());
//			} catch (Exception ex) {
//				logger.error(ex, ex);
//				fail(ex.getMessage());
//			}
//		}
//		for (Triple<String, HttpMethod, Integer> entry : testCombine) {
//			logger.info("================{} {}================", entry.getLeft(), entry.getMiddle());
//			Http1Request rq = client.createReq(entry.getMiddle(), entry.getLeft());
//			try {
//				FullHttpResponse rs = client.sendSync(rq);
//				String s = rs.content().toString(CharsetUtil.UTF_8);
//				logger.info("Response. status={}, body={}", rs.status(), s);
//				int code = rs.status().code();
//				assertEquals(code, entry.getRight());
//				if (code == 200) {
//					assertEquals(s, entry.getMiddle().toString().toLowerCase());
//				}
//			} catch (Exception ex) {
//				logger.error(ex, ex);
//				fail(ex.getMessage());
//			}
//
//		}
//	}
}
