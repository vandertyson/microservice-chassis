package com.viettel.vocs.microchassis.codec.handler.http;

import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.ServerOnlyHandler;
import com.viettel.vocs.microchassis.codec.handler.http.restapi.RestHandler;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class HttpServerHandler
	extends ServerOnlyHandler<HttpRequest, HttpResponse, HttpServerContext>
	implements RestHandler<HttpServerContext> {

	public HttpServerHandler(HttpServerOptions config) {
		super(config);
//		config.enable(ChassisConst.SupportVersion.HTTP_2_0);
//		config.enable(ChassisConst.SupportVersion.HTTP_1_1);
	}

	@Override
	public void log(HttpServerContext serverCtx){
		if (logger.isDebugEnabled())
			logger.debug(String.format("[HServer receive %s request]{ctx=%s, headers=%s, content=%s}",
			serverCtx.getChannelString(), serverCtx.getRequestMethod(), serverCtx.getInMsg().headers(), Arrays.toString(serverCtx.getInData())));
	}

	protected final HttpAPI match(String url) throws NoSuchElementException {
		Pair<Pattern, HttpAPI> patternHttpApiEntry = apiMap.stream().filter(p -> p.getKey().matcher(url).matches()).findFirst().orElse(null);
		if (patternHttpApiEntry != null) return patternHttpApiEntry.getValue();
		else throw new NoSuchElementException("url pattern " + url + " not registered");
	}

	private final List<Pair<Pattern, HttpAPI>> apiMap = new CopyOnWriteArrayList<>();

	private boolean registerApiWithRegex(String regex, HttpAPI api) {
		if (api != null && regex != null && !regex.isEmpty()) {
			apiMap.add(Pair.of(Pattern.compile(regex), api));
			return true;
		}
		return false;
	}

	public final boolean registerApi(String regex, HttpAPI api) { // ~ contain
		return registerApiContain(regex, api);
	}

	public final boolean registerApiContain(String regex, HttpAPI api) {
		return registerApiWithRegex(".*" + regex + ".*", api);
	}

	public final boolean registerApiStartWith(String regex, HttpAPI api) {
		return registerApiWithRegex("^" + regex + ".*", api);
	}

	public final boolean registerApiExact(String regex, HttpAPI api) {
		return registerApiWithRegex("^" + regex + "$", api);
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

	public void disconnectHandle(ChannelHandlerContext ctx) {
		// TODO define this for HTTP2
	}
	@Override
	public void handle(HttpServerContext serverCtx) {
		try { // if registered, process by register, else process by dispatcher of chassis
			HttpAPI httpAPI = match(serverCtx.getInPath());
			if (httpAPI != null) {
				httpAPI.handle(serverCtx);
				return;
			}
		} catch (NoSuchElementException e) {
			if (logger.isDebugEnabled()) logger.error(e);
		}
		dispatch(serverCtx);
	}



}
