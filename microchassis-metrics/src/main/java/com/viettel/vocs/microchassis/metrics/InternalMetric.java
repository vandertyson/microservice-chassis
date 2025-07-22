/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.metrics;

/**
 * @author vttek
 */
public class InternalMetric {
    private InternalMetric() {
    }

    public static final class Client {
        private Client() {
        }

        public static final PrometheusMetric COUNT_HTTP1_RESPONSE_RECEIVE = new AddMetric("CLIENT_count_http1_response_receive");
        public static final PrometheusMetric COUNT_HTTP1_RESPONSE_HANDLE = new AddMetric("CLIENT_count_http1_response_handle");
        public static final PrometheusMetric COUNT_HTTP2_REQUEST_SEND = new AddMetric("CLIENT_count_http2_request_send");
        public static final PrometheusMetric COUNT_HTTP2_REQUEST_FLUSH_SUCCESS = new AddMetric("CLIENT_count_http2_request_flush_success");
        public static final PrometheusMetric COUNT_HTTP2_REQUEST_FLUSH_FAIL = new AddMetric("CLIENT_count_http2_request_flush_fail");
        public static final PrometheusMetric COUNT_HTTP2_RESPONSE_RECEIVE = new AddMetric("CLIENT_count_http2_response_receive");
        public static final PrometheusMetric COUNT_HTTP2_RESPONSE_HANDLE = new AddMetric("CLIENT_count_http2_response_handle");
        public static final PrometheusMetric COUNT_HTTP2_RESPONSE_TIMEOUT = new AddMetric("CLIENT_count_http2_response_timeout");
        public static final PrometheusMetric COUNT_HTTP_CONNECTION_UP = new AddMetric("CLIENT_count_http2_connection_renew");
        public static final PrometheusMetric COUNT_HTTP_CONNECTION_DOWN = new AddMetric("CLIENT_count_http2_connection_down");
        public static final PrometheusMetric TOTAL_BYTES_SEND = new AddMetric("CLIENT_total_bytes_send");
        public static final PrometheusMetric TOTAL_BYTES_RECEIVE = new AddMetric("CLIENT_total_bytes_receive");
    }

    public static final class Server {
        private Server() {
        }

        public static final PrometheusMetric COUNT_HTTP_CONNECTION_UP = new AddMetric("SERVER_count_http_connection_up");
        public static final PrometheusMetric COUNT_HTTP_CONNECTION_DOWN = new AddMetric("SERVER_count_http_connection_down");
        public static final PrometheusMetric COUNT_HTTP1_REQUEST_RECEIVE = new AddMetric("SERVER_count_http1_request_receive");
        public static final PrometheusMetric COUNT_HTTP1_REQUEST_HANDLE = new AddMetric("SERVER_count_http1_request_handle");
        public static final PrometheusMetric COUNT_HTTP1_RESPONSE_SEND = new AddMetric("SERVER_count_http1_response_send");
        public static final PrometheusMetric COUNT_HTTP1_RESPONSE_FLUSH_SUCCESS = new AddMetric("SERVER_count_http1_response_flush_success");
        public static final PrometheusMetric COUNT_HTTP1_RESPONSE_FLUSH_FAIL = new AddMetric("SERVER_count_http1_response_flush_fail");
        public static final PrometheusMetric TOTAL_BYTES_SEND = new AddMetric("SERVER_total_bytes_send");
        public static final PrometheusMetric TOTAL_BYTES_RECEIVE = new AddMetric("SERVER_total_bytes_receive");
        public static final PrometheusMetric HISTO_HTTP1_REQUEST_PAYLOAD_SIZE = new SetMetric("SERVER_gauge_http1_request_payload_size");
        public static final PrometheusMetric HISTO_HTTP1_RESPONSE_PAYLOAD_SIZE = new SetMetric("SERVER_gauge_http1_response_payload_size");
    }
}
