package com.viettel.vocs.microchassis.codec.handler.http;

import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.http.restapi.RestHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public abstract class HttpAPI implements RestHandler<HttpServerContext> {
	protected static final Logger logger = LogManager.getLogger(HttpAPI.class);
	public static Handler<String> makeOnResultStringCallback(HttpServerContext ctx) {
		return result -> {
			try {
				if (result != null) {
					ctx.send(result);
				} else {
					ctx.send(HttpResponseStatus.INTERNAL_SERVER_ERROR);
				}
			} catch (Exception ex) {
				logger.error(ex, ex);
			}
		};
	}
	protected Handler<HttpServerContext> userHander;
	protected HttpAPI(){

	}
	protected HttpAPI(Handler<HttpServerContext> userHander){
		this.userHander = userHander;
	}
	public void log(HttpServerContext serverCtx) {
		if (logger.isDebugEnabled())
			logger.debug(String.format("[HServer receive API %s %s]{ctx=%s, headers=%s, content=%s}",
				serverCtx.getChannelString(), serverCtx.getRequestMethod(), serverCtx.getInPath(), serverCtx.getInMsg().headers(), Arrays.toString(serverCtx.getInData())));
	}

	@Override
	public void handle(HttpServerContext serverCtx) {
		dispatch(serverCtx);
	}

	@Override
	public void serverErrorHandle(HttpServerContext serverCtx, Exception ex) {
		try {
			logger.error(ex, ex);
			serverCtx.send(ex.toString(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
}
