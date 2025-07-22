package com.viettel.vocs.microchassis.connection;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

public class JsonTest {
    private static class MyStrategy implements ExclusionStrategy {
        public Set<String> exclude = new HashSet<>();

        public Set<String> getExclude() {
            return exclude;
        }

        public void setExclude(Set<String> exclude) {
            this.exclude = exclude;
        }

        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return exclude.contains(f.getName());
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    }

    private static class MyObject {
        private String x1;
        private String x2;
        private String x3;

        public MyObject(String x1, String x2, String x3) {
            this.x1 = x1;
            this.x2 = x2;
            this.x3 = x3;
        }
    }

    private static class MyData {
        private String d1;
        private String d2;

        public MyData(String x1, String x2) {
            this.d1 = x1;
            this.d2 = x2;
        }
    }

    @Test
    public static void testExcludeField() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        MyStrategy myStrategy = new MyStrategy();
        Gson gson = gsonBuilder.addSerializationExclusionStrategy(myStrategy).create();

        //obj1
        Set<String> set1 = new HashSet<>();
        set1.add("x1");
        set1.add("x3");
        myStrategy.setExclude(set1);
        MyObject o1 = new MyObject("1", "2", "3");
        System.out.println(gson.toJson(o1));

        //obj2
        Set<String> set2 = new HashSet<>();
        set2.add("d2");
        myStrategy.setExclude(set2);
        MyData data = new MyData("g", "h");
        System.out.println(gson.toJson(data));
    }

}
