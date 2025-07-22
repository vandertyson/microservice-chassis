package com.viettel.vocs.microchassis.exception.parser;

import com.viettel.vocs.microchassis.exception.utils.ParserUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestParserUtils {
    @Test
    public void testGetStackTrace() {
        Exception exception = new Exception();
        Object actual;
        actual = ParserUtils.getStackTrace(exception);

        Assertions.assertTrue(actual instanceof String);
        Assertions.assertNotNull(actual);
    }
}
