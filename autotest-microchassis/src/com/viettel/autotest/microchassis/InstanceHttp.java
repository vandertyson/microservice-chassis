package com.viettel.autotest.microchassis;

import com.viettel.vocs.common.file.JsonParser;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.http.client.HttpInstanceClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;

import java.util.Map;


/**
 * @author tiennn18
 */
public class InstanceHttp {
	public static void main(String[] args) throws Exception {
		Map<String, Object> param = Map.of("bucket", "sws", "objectPath", "sws/tiennn18", "recursive", true);
		String jsonParam = JsonParser.getInstance().writeAsJsonString(param);
		HttpInstanceClient defaultHttp1Instance = ConnectionManager.getDefaultHttpInstance();
		HttpRequest reaquest = defaultHttp1Instance.createReq(HttpMethod.POST, "/api/v1/objects", jsonParam.getBytes());
		reaquest.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
		HttpResponse fullHttpResponse = defaultHttp1Instance.sendSync("172.20.3.160", 8181, reaquest);
		System.out.println(fullHttpResponse.status());
	}
}
