package com.viettel.vocs.mano.reload;

import com.viettel.vocs.common.config.loader.Loader;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class APIReloadManager {
    public static final String CheckVersion = "Check Version";
    private static Logger logger = LogManager.getLogger(APIReloadManager.class);
    private final HashMap<String, ReloadAPI> mapReloadAPI = new HashMap<>();
    private static final AtomicBoolean isReloading = new AtomicBoolean(false);
    @Getter
    private static final APIReloadManager instance = new APIReloadManager();

    public Set<String> getReloadKeys() {
        return mapReloadAPI.keySet();
    }

    public APIReloadManager registerReloadAPI(ReloadAPI _reloadAPI) {
        mapReloadAPI.put(_reloadAPI.getReloadKey(), _reloadAPI);
        return this;
    }

    public void reloadWithRequest(HttpServerContext ctx) {
        try {
            if (!isReloading.compareAndSet(false, true)) {
                try {
                    ctx.send("Fail! Processing another request !", HttpResponseStatus.TOO_MANY_REQUESTS);
                } catch (Exception e1) {
                    logger.error(e1, e1);
                }
                return;
            }
            Pair<HttpResponseStatus, String> result = triggerReloadRequest(
              JsonUtils.getDecoder().fromJson(
                ctx.getInMsg().toString(StandardCharsets.UTF_8),
                ReloadRequest.class));
            if(result!=null) ctx.send(result.getRight(), result.getLeft());
        } catch (Exception e) {
            logger.error(e, e);
            try {
                ctx.send("Fail! Reload fail with exception", HttpResponseStatus.INTERNAL_SERVER_ERROR);
            } catch (Exception e1) {
                logger.error(e1, e1);
            }
        } finally {
            isReloading.set(false);
        }
    }
    public Pair<HttpResponseStatus, String> triggerReloadRequest(ReloadRequest request){
        Pair<HttpResponseStatus, String> result = null;
        if (mapReloadAPI.containsKey(request.getReloadKey())) {
            ReloadAPI reloadAPI = mapReloadAPI.get(request.getReloadKey());
            logger.info("Reload [{}] with request {}", reloadAPI.getReloadName(), JsonUtils.getEncoder().toJson(request));

            result = CheckVersion.equals(request.getReloadType())
              ? reloadAPI.onReloadCheckVersion(request.getReloadInfo())
              : Pair.of(HttpResponseStatus.NOT_FOUND, "Not support reload !");

            if (!CheckVersion.equals(request.getReloadType())) {
                switch (Loader.Source.valueOf(request.getReloadType())) {
                    case DB:
                        result = reloadAPI.onReloadFromSql(request.getReloadInfo());
                        break;
                    case File:
                        if (reloadAPI.isSupportKPIRollback() && request.getIsRollbackVersion()) {
                            if (request.getReloadInfo() == null)
                                request.setReloadInfo(new HashMap<>());
                            request.getReloadInfo().put("isRollback", true);
                        }
                        result = reloadAPI.onReloadFromFile(request.getReloadInfo());
                        break;
                    case PlainText:
                        result = reloadAPI.onReloadFromPlainText(request.getReloadInfo());
                        break;
                    // default: => remain result not support
                }
            }
        }
        return result;
    }

    public void getSupportedAPIReload(HttpServerContext ctx) {
        ArrayList<ReloadAPIInfo> list = new ArrayList<>();
        try {
            mapReloadAPI.values().forEach(reloadAPI -> {
                ReloadAPIInfo reloadAPIInfo = new ReloadAPIInfo();
                reloadAPIInfo.setName(reloadAPI.getReloadName());
                reloadAPIInfo.setKey(reloadAPI.getReloadKey());
                reloadAPIInfo.setListParam(reloadAPI.getListAPIParam());
                reloadAPIInfo.setReloadType(reloadAPI.getListReloadType());

                list.add(reloadAPIInfo);
            });

            String response = JsonUtils.getEncoder().toJson(list);

            if (logger.isInfoEnabled()) logger.info("[getSupportedAPIReload] apiList: {}", response);
            ctx.send(response);
        } catch (Exception ex) {
            logger.error(ex, ex);
        }
    }

}
