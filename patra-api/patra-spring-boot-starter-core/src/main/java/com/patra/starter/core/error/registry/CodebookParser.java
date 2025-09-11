package com.patra.starter.core.error.registry;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.patra.common.error.core.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 错误码册解析器，负责从类路径资源加载错误码配置。
 * 
 * <p>支持的资源格式：
 * <ul>
 *   <li>Properties 格式：{@code META-INF/patra/codebook-*.properties}</li>
 *   <li>JSON 格式：{@code META-INF/patra/codebook-*.json}（需 Jackson 运行时支持）</li>
 * </ul>
 * 
 * <p>Properties 文件约定格式：
 * <pre>{@code
 * REG-C0101.title=Parameter missing
 * REG-C0101.http=422
 * REG-C0101.doc=https://docs.example.com/errors/REG-C0101
 * REG-C0101.owner=registry-team
 * REG-C0101.extras.customField=customValue
 * }</pre>
 * 
 * <p>JSON 文件约定格式：
 * <pre>{@code
 * [
 *   {
 *     "code": "REG-C0101",
 *     "title": "Parameter missing",
 *     "http": 422,
 *     "doc": "https://docs.example.com/errors/REG-C0101",
 *     "owner": "registry-team",
 *     "extras": {
 *       "customField": "customValue"
 *     }
 *   }
 * ]
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public final class CodebookParser {
    
    /**
     * 默认构造器。
     */
    public CodebookParser() {
    }

    public Codebook loadFromProperties(URL url) {
        Codebook cb = new Codebook();
        Properties p = new Properties();
        try (var in = url.openStream();
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            p.load(reader);
        } catch (IOException e) {
            System.err.println("[CodebookLoader] load properties failed: " + e.getMessage());
            return cb;
        }

        // 以 “CODE.field” 分组
        Map<String, Map<String, String>> group = new HashMap<>();
        for (String key : p.stringPropertyNames()) {
            int dot = key.indexOf('.');
            if (dot <= 0) continue;
            String code = key.substring(0, dot);
            String field = key.substring(dot + 1);
            group.computeIfAbsent(code, k -> new HashMap<>()).put(field, p.getProperty(key));
        }

        for (var e : group.entrySet()) {
            String literal = e.getKey();
            Map<String, String> kv = e.getValue();
            ErrorCode code;
            try {
                code = ErrorCode.of(literal);
            } catch (IllegalArgumentException ex) {
                System.err.println("[CodebookLoader] skip invalid code literal: " + literal);
                continue;
            }
            String title = kv.get("title");
            Integer http = parseIntOrNull(kv.get("http"));
            String doc = kv.get("doc");
            String owner = kv.get("owner");

            // extras: 以 "extras." 前缀收集
            Map<String, Object> extras = new LinkedHashMap<>();
            for (var ent : kv.entrySet()) {
                if (ent.getKey().startsWith("extras.")) {
                    extras.put(ent.getKey().substring("extras.".length()), ent.getValue());
                }
            }
            cb.register(new CodebookEntry(code, title, http, doc, owner, extras.isEmpty() ? null : extras));
        }
        return cb;
    }

    /**
     * 使用 Hutool 安全地解析整数，避免异常。
     * 
     * @param value 待解析的字符串值
     * @return 解析后的整数，解析失败时返回 null
     */
    private static Integer parseIntOrNull(String value) {
        return StrUtil.isBlank(value) ? null : Convert.toInt(value.trim(), null);
    }

    // ---------- json----------

    public Codebook loadFromJson(URL url) {
        Codebook cb = new Codebook();
        String json;
        try (var in = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            json = in.lines().reduce(new StringBuilder(), StringBuilder::append, StringBuilder::append).toString();
        } catch (IOException e) {
            System.err.println("[CodebookLoader] read json failed: " + e.getMessage());
            return cb;
        }
        // 期待 List<Map<String,Object>>
        Object list = jacksonReadList(json);
        if (!(list instanceof List<?> arr)) return cb;
        for (Object o : arr) {
            if (!(o instanceof Map<?, ?> map)) continue;
            String literal = Objects.toString(map.get("code"), null);
            if (literal == null) continue;
            ErrorCode code;
            try {
                code = ErrorCode.of(literal);
            } catch (IllegalArgumentException ex) {
                System.err.println("[CodebookLoader] skip invalid code literal: " + literal);
                continue;
            }
            String title = optString(map, "title");
            Integer http = optInt(map, "http");
            String doc = optString(map, "doc");
            String owner = optString(map, "owner");
            Map<String, Object> extras = optMap(map, "extras");
            cb.register(new CodebookEntry(code, title, http, doc, owner, (extras == null || extras.isEmpty()) ? null : extras));
        }
        return cb;
    }

    private static String optString(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
        // 允许空字符串
    }

    private static Integer optInt(Map<?, ?> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        try {
            return Integer.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Object> optMap(Map<?, ?> m, String k) {
        Object v = m.get(k);
        if (v instanceof Map<?, ?> map) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (var e : map.entrySet()) {
                r.put(String.valueOf(e.getKey()), e.getValue());
            }
            return r;
        }
        return null;
    }

    // ---------- 资源发现与 Jackson 反射桥 ----------

    /**
     * ObjectMapper().readValue(json, List.class) 反射调用
     */
    private static Object jacksonReadList(String json) {
        try {
            Class<?> omClz = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            Object om = omClz.getConstructor().newInstance();
            return omClz.getMethod("readValue", String.class, Class.class)
                    .invoke(om, json, List.class);
        } catch (Throwable t) {
            System.err.println("[CodebookLoader] Jackson reflection failed: " + t.getMessage());
            return null;
        }
    }
}
