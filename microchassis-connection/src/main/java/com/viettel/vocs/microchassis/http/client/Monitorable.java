package com.viettel.vocs.microchassis.http.client;

import com.viettel.vocs.microchassis.base.Endpoint;

public interface Monitorable {

    String getId();

    int getCheckInterval();

    void onURIup(Endpoint uri);

    void onURIdown(Endpoint uri);

    int getMaxRetry();

    String getCheckPath();

    long getTimeoutMs();
}

