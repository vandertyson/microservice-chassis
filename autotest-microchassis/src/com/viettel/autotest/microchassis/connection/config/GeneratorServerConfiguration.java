package com.viettel.autotest.microchassis.connection.config;

import lombok.Getter;

public class GeneratorServerConfiguration extends com.viettel.vocs.microchassis.connection.config.ServerConfiguration implements GeneratorConfig {
	@Getter int fakeTPS = 0; // 0 mean no limit
	@Getter public int nWindowToRotate = 10;
	@Getter public int generateThreads = 0;
	@Getter public int fakePayloadSize =  10 * 1024;
	@Getter public int generateThreadsPerfTPS4kB = 50000; // avgCurTime 1 gen khoang 6micro ~ 150kTPS, nhung khi long run thi 1 thread corequeue chi enqueue duoc toi da 66kTPS, mpmc 94kTPS -> set threshold 50kTPS

	public GeneratorServerConfiguration(String serverId) {
		super(serverId);
	}

	public GeneratorServerConfiguration(String id, int port, String lbrId, int maxOpenConnection, int minOpenConnection, int maxHostConnectable, String limitConnectionMode) {
		super(id, port, lbrId, maxOpenConnection, minOpenConnection, maxHostConnectable, limitConnectionMode);
	}

	public GeneratorServerConfiguration(String id, int port) {
		super(id, port);
	}

	public GeneratorServerConfiguration(String id, int port, String lbrId) {
		super(id, port, lbrId);
	}
}
