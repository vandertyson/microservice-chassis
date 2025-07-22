package com.viettel.autotest.microchassis.feature;

import com.viettel.vocs.common.config.ConfigUtils;
import com.viettel.vocs.common.config.value.IntegerConfig;
import com.viettel.vocs.common.config.value.StringConfig;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 */
public class DnsTest {
	// CHASSIS_RESOLVE_NAME=headless-svc.tiennn18-test;CHASSIS_RESOLVE_LENGTH=200;CHASSIS_RESOLVE_LOOP=5
	// java -Xmx1G -cp "../lib/*:../lib-common/*" com.viettel.autotest.microchassis.feature.DnsTest 2>&1
	static final String hostnameKey = "chassis.resolve.name";
	static final String desiredLength = "chassis.resolve.length"; //
	static final String replayKey = "chassis.resolve.loop"; //
	static final StringConfig hostname = new StringConfig(ConfigUtils.propToManoEnv(hostnameKey), hostnameKey, "headless-svc.tiennn18-test");
	static final IntegerConfig desired = new IntegerConfig(ConfigUtils.propToManoEnv(desiredLength), desiredLength,200);
	static final IntegerConfig replay = new IntegerConfig(ConfigUtils.propToManoEnv(replayKey), replayKey, 10);
	public static void main(String[] args) {
		String name = hostname.get();
		long expect = desired.get();
		AtomicLong repeat = new AtomicLong(replay.get());
		long i = repeat.get();
		long countSuccess = 0;
		List<String> wrongLines = new ArrayList<>(); // collect wrong lines
		while ((i = repeat.decrementAndGet())>=0) {
			try {
				InetAddress[] inetResolved = InetAddress.getAllByName(name);
				String line = String.format("[%d] %s expect %d is %d [%s]\n", i, name, expect, inetResolved.length, Arrays.toString(inetResolved));
				System.out.printf(line);
				if(inetResolved.length == expect) countSuccess += 1;
				else wrongLines.add(line);
			} catch (Throwable e){
				String exLine = "Oof, exception: "+e.getMessage();
				System.out.println(exLine);
				wrongLines.add(exLine);
				e.printStackTrace();
			}
		}

		System.out.printf("Final result: success %d, wrong {%s}", countSuccess, wrongLines.stream().map(line -> "["+line+"]").collect(Collectors.joining(", ")));
		if(replay.get() == countSuccess) System.out.println("All matched");
	}
}
