package com.viettel.vocs.microchassis.depricated.restapi;

import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class APIIdentify {
    private final HttpMethod method;
    private final String pattern;

    protected APIIdentify(HttpMethod method, String pattern) {
        this.method = method;
        this.pattern = pattern;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        APIIdentify that = (APIIdentify) o;

        if (!Objects.equals(method, that.method)) return false;
        return Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        int result = method != null ? method.hashCode() : 0;
        result = 31 * result + (pattern != null ? pattern.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "APIIdentify{" +
                "method=" + method +
                ", pattern='" + pattern + '\'' +
                '}';
    }

    public abstract Triple<Boolean, String, Map<String, String>> match(String path);
}

class APIIdentifierV1 extends APIIdentify {
    private static final Pattern PATTERN = Pattern.compile("\\{(\\w*)\\}");
    private final String apiRoot;
    private final Map<String, Integer> paramPositionMap = new LinkedHashMap<>();
    private final int rootIndex;

    public APIIdentifierV1(HttpMethod method, String pattern) {
        super(method, pattern);
        Matcher m = PATTERN.matcher(pattern);
        while (m.find()) {
            String substring = pattern.substring(0, m.start());
            int countMatches = StringUtils.countMatches(substring, "/");
            paramPositionMap.put(m.group(1), countMatches);
        }
        rootIndex = pattern.indexOf("{");
        if (rootIndex <= 0) {
            apiRoot = formatRoot(pattern);
        } else {
            String substring = pattern.substring(0, rootIndex);
            apiRoot = formatRoot(substring);
        }
    }

    private String formatRoot(String api) {
        int i = api.length() - 1;
        if (api.substring(i).equals("/")) {
            return api.substring(0, i);
        }
        return api;
    }

//    public static void main(String[] args) {
//        String pattern = "/api/v1/towers/{towerID}";
//        String[] split = pattern.split("/");
//        Map<String, Integer> mapIndex = new HashMap<>();
//        int id = 0;
//        int count = 0;
//        for (String s : split) {
//            if (s.length() > 0) {
//                count++;
//                if (s.startsWith("{") && s.endsWith("}")) {
//                    mapIndex.put(s.substring(1, s.length() - 1), id);
//                }
//                id++;
//            }
//        }
//        System.out.println("count " + count);
//        System.out.println("map param " + mapIndex);
//    }

    public String getApiRoot() {
        return apiRoot;
    }

    public Triple<Boolean, String, Map<String, String>> match(String url) {
        //no param url. check equal
        if (rootIndex == -1) {
            if (formatRoot(url).equals(apiRoot)) {
                return new ImmutableTriple<>(true, null, null);
            } else {
                int i = url.indexOf(apiRoot);
                if (i == 0) {
                    return new ImmutableTriple<>(true, null, null);
                } else {
                    return new ImmutableTriple<>(false, null, null);
                }
            }
        }
        //with param url.
        if (url.length() < apiRoot.length()) {
            return new ImmutableTriple<>(false, null, null);
        }
        if (url.equals(apiRoot)) {
            return new ImmutableTriple<>(true, null, null);
        }
        //url contains
        try {
            String root = url.substring(0, rootIndex);
            if (!formatRoot(root).equals(apiRoot)) {
                return new ImmutableTriple<>(false, null, null);
            }
        } catch (Exception ex) {
            return new ImmutableTriple<>(false, null, null);
        }
        Map<String, String> mapExtract = new HashMap<>();
        String missingParam = null;
        boolean match = true;
        for (Map.Entry<String, Integer> entry : paramPositionMap.entrySet()) {
            Integer idx = entry.getValue();
            String name = "(\\/\\w*){" + idx + "}";
            Pattern p2 = Pattern.compile(name);
            Matcher m2 = p2.matcher(url);
            if (m2.find()) {
                String x = m2.group(1);
                mapExtract.put(entry.getKey(), x.substring(1));
            } else {
                missingParam = entry.getKey();
                match = false;
                break;
            }
        }
        return new ImmutableTriple<>(match, missingParam, mapExtract);
    }
}
class APIIdentifierV2 extends APIIdentify {
    private final String apiRoot;
    private final Map<Integer, String> paramPositionMap = new HashMap<>();
    private final int rootIndex;
    private final Map<Integer, String> nonParamElements = new HashMap<>();

    public APIIdentifierV2(HttpMethod method, String pattern) {
        super(method, pattern);
        rootIndex = pattern.indexOf("{");
        if (rootIndex <= 0) {
            apiRoot = formatRoot(pattern);
        } else {
            String substring = pattern.substring(0, rootIndex);
            apiRoot = formatRoot(substring);
        }
        String[] split = pattern.split("/");
        int id = 0;
        for (String s : split) {
            if (s.length() > 0) {
                if (s.startsWith("{") && s.endsWith("}")) {
                    paramPositionMap.put(id, s.substring(1, s.length() - 1));
                } else {
                    nonParamElements.put(id, s);
                }
            }
            id++;
        }
    }

    private String formatRoot(String api) {
        int i = api.length() - 1;
        if (api.substring(i).equals("/")) {
            return api.substring(0, i);
        }
        return api;
    }

    public String getApiRoot() {
        return apiRoot;
    }

    private static final Triple<Boolean, String, Map<String, String>> NON_MATCH = new ImmutableTriple<>(false, null, null);

    public Triple<Boolean, String, Map<String, String>> match(String url) {
        if (url.indexOf(apiRoot) != 0) {
            return NON_MATCH;
        }
        String toCheck = formatRoot(url);
        String[] split = toCheck.split("/");
        //check nonParamElements first
        Map<String, String> paramExtracted = new HashMap<>();
        Map<Integer, String> nonParamExtracted = new HashMap<>();
        for (int i = 0; i < split.length; i++) {
            if (split[i].isEmpty()) {
                continue;
            }
            String s = paramPositionMap.get(i);
            if (s != null) {
                paramExtracted.put(s, split[i]);
            } else {
                nonParamExtracted.put(i, split[i]);
            }
        }
        if (!nonParamExtracted.equals(nonParamElements)) {
            return NON_MATCH;
        }
        if (paramExtracted.size() == paramPositionMap.size() || paramExtracted.isEmpty()) {
            return new ImmutableTriple<>(true, null, paramExtracted);
        }
        return NON_MATCH;
    }
}