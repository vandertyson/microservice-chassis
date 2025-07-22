package com.viettel.vocs.mano.reload;

import java.util.Map;

public class ReloadRequest {
    private String reloadKey;
    private String reloadType;
    private Map<String, Object> reloadInfo;
    private boolean isRollbackVersion;

    public boolean getIsRollbackVersion() {
        return isRollbackVersion;
    }

    public ReloadRequest setIsRollbackVersion(boolean rollbackVersion) {
        isRollbackVersion = rollbackVersion;
        return this;
    }

    public String getReloadKey() {
        return reloadKey;
    }

    public ReloadRequest setReloadKey(String reloadKey) {
        this.reloadKey = reloadKey;
        return this;
    }

    public String getReloadType() {
        return reloadType;
    }

    public ReloadRequest setReloadType(String reloadType) {
        this.reloadType = reloadType;
        return this;
    }

    public Map<String, Object> getReloadInfo() {
        return reloadInfo;
    }

    public ReloadRequest setReloadInfo(Map<String, Object> reloadInfo) {
        this.reloadInfo = reloadInfo;
        return this;
    }
}
