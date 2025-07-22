package com.viettel.vocs.microchassis.http.client;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import com.viettel.vocs.microchassis.base.ChassisConfig;
import io.netty.handler.logging.LogLevel;

public interface HttpChannelInitializer {
	LogLevel frameLoggerLevel = LogLevel.valueOf(ChassisConfig.ConnectionConfig.Http2Config.Http2DebugId.get());
}


