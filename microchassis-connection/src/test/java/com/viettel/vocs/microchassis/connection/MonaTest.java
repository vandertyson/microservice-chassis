package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author tiennn18
 */
public class MonaTest {
	static {
		ConnectionManager.getInstance();
	}
	String data = "{\"grant_type\":\"client_credentials\"}";
	String curlCommand = "curl --location --request POST 'http://172.20.3.174:9981/users/auth' --header 'Authorization: Basic b2NzX3N5c3RlbToxMjM0NTZhQEE=' --header 'Content-Type: application/json' -d '"+data+"'";
	HttpClientOptions config = new HttpClientOptions("172.20.3.174", 9981, "mano");

	protected HttpRequest createAuthReq(HttpClient client){
		HttpRequest authReq = client.createReq(HttpMethod.POST, "/users/auth", data.getBytes());
		authReq.headers().set("Authorization", "Basic b2NzX3N5c3RlbToxMjM0NTZhQEE=");
		authReq.headers().set("Content-Type", "application/json");
		return authReq;
	}
	static class AuthResponse{
		String access_token;
		String token_type;
		long expires_in;
		String userId;

		@Override
		public String toString() {
			return String.format("AuthResponse{\n  access_token: %s\n  token: %s\n  expires: %d\n  userId: %s\n}",
				access_token, token_type, expires_in, userId);
		}
		public boolean validate(){
			return access_token != null
				&& token_type !=null
				&& expires_in > 0
				&& userId !=null
				&& !access_token.isEmpty()
				&& !userId.isEmpty()
				&& !token_type.isEmpty();
		}
	}
	@Test
	void authen() throws Exception{
		try(HttpClient client = config.newClientHttp1Upgrade()){
			client.start(new HttpClientHandler(config));
			HttpResponse response = client.sendSync(createAuthReq(client), IDfy.generateNewId());
			AuthResponse authResponse = JsonUtils.getDecoder().fromJson(response.toString(StandardCharsets.UTF_8), AuthResponse.class);
//			System.out.println("REs auth "+ authResponse);
			assertTrue(authResponse.validate());
		}
	}
}
