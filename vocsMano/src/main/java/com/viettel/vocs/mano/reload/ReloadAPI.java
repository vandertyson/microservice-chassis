package com.viettel.vocs.mano.reload;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;

public abstract class ReloadAPI {
    public abstract String getReloadKey();

    public abstract String getReloadName();

    public abstract List<ReloadAPIParam> getListAPIParam();

    public abstract List<String> getListReloadType();

    public abstract Pair<HttpResponseStatus, String> onReloadFromSql(Map<String, Object> _reloadInfo);

    public abstract Pair<HttpResponseStatus, String> onReloadFromFile(Map<String, Object> _reloadInfo);
    public Pair<HttpResponseStatus, String> onReloadFromPlainText(Map<String, Object> _reloadInfo){
        return Pair.of(HttpResponseStatus.NOT_FOUND, "NOT_IMPLEMENT");
    }

    public boolean isSupportKPIRollback() {
        return false;
    }

    public Pair<HttpResponseStatus, String> onReloadCheckVersion(Map<String, Object> _reloadInfo) {
        return Pair.of(HttpResponseStatus.NOT_FOUND, "NOT_IMPLEMENT");
    }
}
