package com.viettel.autotest.microchassis.lib.sdk;

import com.viettel.vocs.mano.model.BlueGreenUpdate;
import com.viettel.vocs.mano.service.Mano1Handler;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpAPI;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author tiennn18
 */

/**
 * @author vttek
 */
public class HttpManoServerHandler extends Mano1Handler {

	private static Logger logger = LogManager.getLogger(HttpManoServerHandler.class);

	public HttpManoServerHandler(HttpServerOptions config) {
		super(config);
		registerApiExact("/reloadPolicyConfig", new HttpAPI() {
			@Override
			public void serverErrorHandle(HttpServerContext serverCtx, Exception ex) {
				logger.error(ex, ex);
				serverCtx.send("Reload " + serverCtx.getInPath() + " fail", HttpResponseStatus.NOT_MODIFIED);
			}

			@Override
			public void handle(HttpServerContext _ctx) {
				logger.info("Before Reload Policy Config");
				_ctx.send("BUMF");
			}
		});
	}

	@Override
	public void healthCheck(HttpServerContext ctx) {
		try {
			ctx.send("{\"status\":\"UP\"}");
		} catch (Exception ex) {
			logger.error(ex, ex);
		}
	}

	@Override
	public void metrics(HttpServerContext serverContext) {
		try {
			serverContext.send(HttpResponseStatus.UNAUTHORIZED);
		} catch (Exception ex) {
			logger.error(ex, ex);
		}
	}

	@Override
	public void serverErrorHandle(HttpServerContext serverCtx, Exception e) {
		logger.error(e, e);

	}

	@Override
	public void shutdown(HttpServerContext ctx) {
		try {

		} catch (Exception ex) {
			logger.error(ex, ex);
		}
	}

	@Override
	public void blueGreenUpdate(HttpServerContext httpServerContext, BlueGreenUpdate blueGreenUpdate) {

	}
}


// curl http://localhost:9000/reloadPolicyConfig -X POST -d '{"reloadKey": "abc"}' -H "mess_id: 51dd9434-22d3-4109-954c-8e6d0dd0873e" -H "content-type: application/json" -H "test_info: 1;1" -H "test_url: http://kttd-vtt-volte:8888" -H "host: rx-pcp:9000" -H "content-length: 1108" -H "x-http2-scheme: http" -H "user-agent: vOCS4.0(Java/11.0.8)" -H "connection: keep-alive"