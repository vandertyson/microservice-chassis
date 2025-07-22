package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;

public class ServerCounter extends PeerCounter { // server hien tai chua can dieu toc, chi can CC monitor
    /**
     * Create counter on each specific (renew) channel, routeStrategy can be changed, so routeStrategy determined at the time channel created,
     * activeConnMode=FrontPressureConnectionMode create from routeStrategy at runtime, then state created at runtime too
     */
    public ServerCounter(ServerConfiguration config) {
        super(ConnectionMode.newState(null), config.sendTimeoutMs); // for server push monitor timeout, no need to control now
    }

    @Override
    public int getSentCC() {
        return super.getSentCC();
    }
}
