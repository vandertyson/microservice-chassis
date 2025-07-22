package com.viettel.autotest.microchassis.connection.config;

import lombok.Getter;

/**
 * @author tiennn18
 */
public class HttpGeneratorServerConfiguration extends com.viettel.vocs.microchassis.http.config.HttpServerOptions  implements GeneratorConfig {
	@Getter
	int fakeTPS = 0; // 0 mean no limit
	@Getter public int nWindowToRotate = 10;
	@Getter public int generateThreads = 0;
	@Getter public int fakePayloadSize =  10 * 1024;
	@Getter public int generateThreadsPerfTPS4kB = 50000; // avgCurTime 1 gen khoang 6micro ~ 150kTPS, nhung khi long run thi 1 thread corequeue chi enqueue duoc toi da 66kTPS, mpmc 94kTPS -> set threshold 50kTPS

	public HttpGeneratorServerConfiguration(String id, int port) {
		super(id, port);
	}

	public HttpGeneratorServerConfiguration(String serverId) {
		super(serverId);
	}
}
