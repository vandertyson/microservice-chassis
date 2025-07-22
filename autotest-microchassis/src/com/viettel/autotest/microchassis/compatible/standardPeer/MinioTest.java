package com.viettel.autotest.microchassis.compatible.standardPeer;

/**
 * @author tiennn18
 */

import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.exception.ClientException;
import com.viettel.vocs.microchassis.http.client.HttpInstanceClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import io.netty.handler.codec.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author tiennn18
 */
public class MinioTest {
	static HttpInstanceClient http1InstanceClient;

	static {
		try {
			http1InstanceClient = ConnectionManager.getDefaultHttpInstance();
		} catch (ClientException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws Exception {
		Map<String, Object> data = Map.of("bucket", "file-policy", "objectPath", "/system/appConfig/25/meta_data_version.json" , "recursive", false);
		HttpRequest req = http1InstanceClient.createReq(HttpMethod.POST, "/api/v1/objects",  JsonUtils.toString(data).getBytes(StandardCharsets.UTF_8));
		req.headers().set("Content-Type", "application/json");
		HttpResponse response = http1InstanceClient.sendSync(new Endpoint("172.20.3.160", "172.20.3.160", 8181), req);
		System.out.println(response.status());
		System.out.println(response.toStringUTF8());
	}
}
