package com.viettel.vocs.microchassis.exception.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ParserUtils {
    private ParserUtils() {
    }

    public static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
