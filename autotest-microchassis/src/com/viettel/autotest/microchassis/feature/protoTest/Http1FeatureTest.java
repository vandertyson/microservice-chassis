/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.autotest.microchassis.feature.protoTest;

import com.viettel.vocs.microchassis.http.config.HttpClientOptions;

public class Http1FeatureTest extends HttpFeatureTest {
	public void setUpClient() {
		try {
			HttpClientOptions config = new HttpClientOptions("localhost", port, "test");
			config.setTo3rdParty(true);
			config.setAutoDecompose(false);
			config.sendTimeoutMs = 3000;
			(client = config.newClientHttp1Only()).start(clientHandler.apply(config));
			logger.info("===================CLIENT FOR TEST CONNECTED===================");
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
}
