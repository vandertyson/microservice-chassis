package com.viettel.vocs.microchassis.depricated.restapi;

import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.codec.handler.http.restapi.*;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.http.HttpMethod.*;

public class UrlPatternHandler extends HttpServerHandler {
	private static final Logger logger = LogManager.getLogger(UrlPatternHandler.class);
	Map<APIIdentify, Handler> mapHandlers = new ConcurrentHashMap<>();
	Map<HttpMethod, List<APIIdentify>> groupByMethod = new ConcurrentHashMap<>();
	Handler<RejectedCtxHolder> undefinedHandler;
	Handler<ErrorCtxHolder> exceptionHandler = ectx -> ectx.get().send(ExceptionUtils.getStackTrace(ectx.getException()), HttpResponseStatus.INTERNAL_SERVER_ERROR);

	public UrlPatternHandler(HttpServerOptions config) {
		super(config);
	}

	public Handler<ErrorCtxHolder> getExceptionHandler() {
		return exceptionHandler;
	}

	public void setExceptionHandler(Handler<ErrorCtxHolder> exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public final void handle(HttpServerContext context) {
		try {
			HttpMethod requestMethod = context.getRequestMethod();
			String path = context.getInPath();
			List<APIIdentify> urlPatternHandlers = groupByMethod.get(requestMethod);
			if (urlPatternHandlers == null) {
				invokeUserHandler(undefinedHandler, new AcceptedCtxHolder(context));
				return;
			}
			for (APIIdentify key : urlPatternHandlers) {
				Triple<Boolean, String, Map<String, String>> match = key.match(path);
				Boolean matched = match.getLeft();
				Map<String, String> extracted = match.getRight();
				if (!matched) {
					continue;
				}
				logger.info("pattern match. pattern={}, param={}", key, extracted);
				AcceptedCtxHolder acceptedServerContext = new AcceptedCtxHolder(context);
				acceptedServerContext.setUrlParams(extracted);
				Handler handler = mapHandlers.get(key);
				invokeUserHandler(handler, acceptedServerContext);
				return;
			}
			invokeUserHandler(undefinedHandler, new RejectedCtxHolder(context));
		} catch (Exception ex) {
			try {
				ErrorCtxHolder errorContext = new ErrorCtxHolder(context);
				errorContext.setException(ex);
				exceptionHandler.handle(errorContext);
			} catch (Exception e) {
				logger.error(e, e);
			}
			logger.error(ex, ex);
		}
	}

	private void invokeUserHandler(Handler handler, AtomicReference<HttpServerContext> context) {
		ChassisThreadManager.hsHandlerPool.execute(() -> {
			try {
				handler.handle(context);
			} catch (Exception ex) {
				ErrorCtxHolder errorContext = new ErrorCtxHolder(context.get());
				errorContext.setException(ex);
				exceptionHandler.handle(errorContext);
				logger.error(ex, ex);
			}
		});
	}


	public void registerPattern(HttpMethod method, String pattern, Handler<HttpServerContext> handler) {
//        APIIdentifierV1 APIIdentifierV1 = new APIIdentifierV1(method, pattern);
		APIIdentifierV2 APIIdentifier = new APIIdentifierV2(method, pattern);
		mapHandlers.put(APIIdentifier, handler);
		groupByMethod.computeIfAbsent(method, s -> new CopyOnWriteArrayList<>()).add(APIIdentifier);
		logger.info("pattern register. {}, method={}, size={}", APIIdentifier, method, mapHandlers.size());
	}

	public void registerUndefinedHandler(Handler<RejectedCtxHolder> handler) {
		this.undefinedHandler = handler;
	}

	public void registerPattern(String pattern, RestHandler<HttpServerContext> restHandler) {
		List<Class<?>> allInterfaces = ClassUtils.getAllInterfaces(restHandler.getClass());
		for (Class<?> inter : allInterfaces) {
			if (inter.equals(DeleteHandler.class))
				registerPattern(DELETE, pattern, restHandler::deleteHandle);
			else if (inter.equals(GetHandler.class))
				registerPattern(GET, pattern, restHandler::getHandle);
			else if (inter.equals(PutHandler.class))
				registerPattern(PUT, pattern, restHandler::putHandle);
			else if (inter.equals(PostHandler.class))
				registerPattern(POST, pattern, restHandler::postHandle);
			else if (inter.equals(PatchHandler.class))
				registerPattern(PATCH, pattern, restHandler::patchHandle);
		}
	}

	public static class RejectedCtxHolder extends AtomicReference<HttpServerContext> {
		String missingParam;
		String matchedPattern;

		public RejectedCtxHolder(HttpServerContext ctx) {
			super(ctx);
		}

		public String getMissingParam() {
			return missingParam;
		}

		public String getMatchedPattern() {
			return matchedPattern;
		}
	}

	public static class ErrorCtxHolder extends AtomicReference<HttpServerContext> {
		private Throwable exception;

		public Throwable getException() {
			return exception;
		}

		public void setException(Throwable exception) {
			this.exception = exception;
		}

		public ErrorCtxHolder(HttpServerContext context) {
			super(context);
		}
	}

	public static class AcceptedCtxHolder extends AtomicReference<HttpServerContext> {
		public AcceptedCtxHolder(HttpServerContext context) {
			super(context);
		}

		Map<String, String> UrlParams;

		public Map<String, String> getUrlParams() {
			return UrlParams;
		}

		public void setUrlParams(Map<String, String> urlParam) {
			UrlParams = urlParam;
		}
	}
}
