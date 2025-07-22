package com.viettel.vocs.microchassis.exception.utils;

import com.viettel.vocs.common.LRUMapAsCache;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestLRUMapAsCache {
    @Test
    public void testMap() {
        int maxEntries = 2;
        LRUMapAsCache map = new LRUMapAsCache(maxEntries);
        map.setDropHandler(entry -> System.out.println("drop key =" + entry.getKey() + ", drop value =" + entry.getValue()));
        map.put("abc1", "value1");
        map.put("abc2", "value2");
        map.put("abc3", "value3");
        map.put("abc4", "value4");
        map.entrySet().stream().forEach(System.out::println);

        Assertions.assertEquals(maxEntries, map.size());
    }
}
