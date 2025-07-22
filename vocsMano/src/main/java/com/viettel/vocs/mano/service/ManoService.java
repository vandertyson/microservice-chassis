/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.mano.service;

import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.Endpoint;
import lombok.Getter;
import lombok.ToString;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author vttek
 */
public class ManoService {
    @ToString
    public enum ManoServiceType {
        API(ChassisConfig.ManoConfig.MANO_AUTHORIZATION_SERVER.get(), "/users/auth"),
        AUTHEN(ChassisConfig.ManoConfig.MANO_AUTHORIZATION_SERVER.get(), "/users/auth"),
        ALARM(ChassisConfig.ManoConfig.MANO_RECEIVED_ALARM_URI.get(), "/cnffm/v1/alarms/notifications"),
        NOTIFICATION(ChassisConfig.ManoConfig.VNFC_NOTIFICATION_ENDPOINT.get(), "/cnflcm/v1/cnfc_status"),
        METRIC(ChassisConfig.ManoConfig.MANO_METRIC_RECEIVE_URI.get(), "/metric/v1/import");
        @Getter private final String path;
        @Getter private final int port;
        @Getter private final String host;
        public Endpoint getEndpoint(){
            return Endpoint.newEndpoint(host, port);
        }
        ManoServiceType(String fullEndpoint, String defaultPath) {
            String defaultHost = ChassisConfig.ManoConfig.DEFAULT_MANO_HOST.check();
            int defaultPort = ChassisConfig.ManoConfig.DEFAULT_MANO_PORT.check().intValue();
            URL endpoint = null;
            try {
                if(fullEndpoint != null && !fullEndpoint.isEmpty()) endpoint = new URL(fullEndpoint);
            } catch (MalformedURLException ignored) {
            }
            host = endpoint != null ? endpoint.getHost() : defaultHost;
            port = endpoint != null ? endpoint.getPort() : defaultPort;
            path = endpoint != null ? endpoint.getPath() : defaultPath;
        }
    }

}
