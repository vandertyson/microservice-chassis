package com.viettel.vocs.microchassis.http.config;

import com.viettel.vocs.microchassis.connection.config.SslConfiguration;
import io.netty.handler.codec.http.HttpScheme;

/**
 * @author tiennn18
 */
interface HttpOptions {
	boolean isSsl();

	HttpOptions setSslConfiguration(SslConfiguration sslConfiguration);

	default HttpScheme scheme() {
		return isSsl() ? HttpScheme.HTTPS : HttpScheme.HTTP;
	}
}
