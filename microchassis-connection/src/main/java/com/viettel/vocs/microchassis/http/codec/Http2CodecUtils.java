package com.viettel.vocs.microchassis.http.codec;

import io.netty.handler.codec.http2.Http2HeadersEncoder;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Http2CodecUtils {
    private static final Logger logger = LogManager.getLogger(Http2CodecUtils.class);
    public static Http2HeadersEncoder.SensitivityDetector CUSTOM_SENSITIVE_HTTP2_HEADERS = (name, value) -> {
        //sonpt26(2025/07/14): danh dau 1 so header la sensitive de khong chay HPACK compression, tranh cao tai CPU
        String toStr = name.toString();
        if (logger.isDebugEnabled()) {
            logger.debug("CUSTOM_SENSITIVE_HTTP2_HEADERS is checking. key={}, value={}", toStr, value);
        }
        return "location".equalsIgnoreCase(toStr) ||
                "smpolicy_id".equalsIgnoreCase(toStr) ||
                "mess_id".equalsIgnoreCase(toStr);
    };

    static {
        try {
            String neverStr = System.getenv("HTTP2_NEVER_COMPRESS_HEADERS");
            String alwaysCompress = System.getenv("HTTP2_ALWAYS_COMPRESS_HEADERS");
            if (!StringUtils.isEmpty(alwaysCompress)) {
                CUSTOM_SENSITIVE_HTTP2_HEADERS = Http2HeadersEncoder.NEVER_SENSITIVE;
                logger.info("Http2 always compress header");
            } else {
                if (!StringUtils.isEmpty(neverStr)) {
                    CUSTOM_SENSITIVE_HTTP2_HEADERS = Http2HeadersEncoder.ALWAYS_SENSITIVE;
                    logger.info("Http2 never compress header");
                } else {
                    String str = System.getenv("CUSTOM_SENSITIVE_HTTP2_HEADERS");
                    if (!StringUtils.isEmpty(str)) {
                        String[] split = str.split(";");
                        CUSTOM_SENSITIVE_HTTP2_HEADERS = (name, value) -> {
                            //sonpt26(2025/07/14): danh dau 1 so header la sensitive de khong chay HPACK compression, tranh cao tai CPU
                            String toStr = name.toString();
                            if (logger.isDebugEnabled()) {
                                logger.debug("CUSTOM_SENSITIVE_HTTP2_HEADERS from env is checking. key={}, value={}", toStr, value);
                            }
                            for (String s : split) {
                                if (s.equalsIgnoreCase(toStr)) {
                                    return true;
                                }
                            }
                            return false;
                        };
                    }
                }
            }
            logger.info("H2 custom header check location: {}", CUSTOM_SENSITIVE_HTTP2_HEADERS.isSensitive("location", "http://172.20.3.59/smpolicy/9124242422"));
            logger.info("H2 custom header check mess_id: {}", CUSTOM_SENSITIVE_HTTP2_HEADERS.isSensitive("location", "x_sadiwer2432dasd"));
            logger.info("H2 custom header check smpolicy_id: {}", CUSTOM_SENSITIVE_HTTP2_HEADERS.isSensitive("location", "http://172.20.3.59/smpolicy/9124242422"));
        } catch (Exception ex) {
            logger.error(ex, ex);
        }
    }
}
