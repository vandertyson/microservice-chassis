/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.autotest.microchassis.feature.protoTest;

import com.viettel.vocs.microchassis.http.config.HttpClientOptions;

public class Http2FeatureTest extends HttpFeatureTest {
	public void setUpClient() {
		try {
			HttpClientOptions options = new HttpClientOptions("localhost", port, "test");
			options.setTo3rdParty(true);
			(client = options.newClientHttp2Only()).start(clientHandler.apply(options));
			logger.info("===================CLIENT FOR TEST CONNECTED===================");
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
}
