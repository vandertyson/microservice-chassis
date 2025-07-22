package com.viettel.vocs.mano.service;

import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpAPI;
import com.viettel.vocs.common.file.YamlUtils;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.List;
import java.util.Map;
class ManoActionHandler extends HttpAPI {

    @Override
    public void handle(HttpServerContext ctx) {
        Map<String, List<String>> queryStringParams = ctx.getInParams();
        boolean authen = queryStringParams.containsKey("authen");
        boolean notify = queryStringParams.containsKey("notify");
        boolean clear = queryStringParams.containsKey("clear");
        boolean stat = queryStringParams.containsKey("stat");
        if (stat) {
            ManoIntegration.onInstance(m -> ctx.send(YamlUtils.objectToPrettyYaml(Map.of("status", m, "detail", m.getMapDetail()))));
        }
        if (authen) {
            ManoIntegration.onInstance(instance -> instance.authen(makeOnResultStringCallback(ctx)));
            return;
        }
        if (notify) {
            ManoIntegration.onInstance(instance -> instance.notification(makeOnResultStringCallback(ctx)));
            return;
        }
        if (clear) {
            List<String> key = queryStringParams.get("target");
            if (key == null || key.isEmpty()) {
                ctx.send(HttpResponseStatus.SERVICE_UNAVAILABLE);
                return;
            }
            String s = key.get(0);
            Handler<String> handler = makeOnResultStringCallback(ctx);
            ManoIntegration.clear(handler, s);
            return;
        }
        ctx.send(HttpResponseStatus.NOT_FOUND);
    }
}
