package com.patra.starter.core.error.codec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.MapType;
import com.patra.common.error.core.ErrorCode;
import com.patra.common.error.core.ErrorSpec;
import com.patra.common.error.core.PlatformError;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.patra.starter.core.error.codec.ProblemJson.*;

/**
 * 基于 Jackson 的 PlatformError 编解码器。
 * - 写出：手工构建 Map，保证时间/URI 按文本输出，避免 JSR310/module 绑定
 * - 读入：宽松解析，容忍缺字段与类型不匹配（例如 status 为字符串）
 */
public class JacksonPlatformErrorCodec implements PlatformErrorCodec {

    private final ObjectMapper mapper;
    private final MapType mapStringObjType;

    /**
     * 使用给定 ObjectMapper。建议配置：
     * - FAIL_ON_UNKNOWN_PROPERTIES = false
     * - SerializationInclusion = NON_NULL
     */
    public JacksonPlatformErrorCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.mapStringObjType = this.mapper.getTypeFactory()
                .constructMapType(Map.class, String.class, Object.class);
    }

    /** 使用默认配置的 ObjectMapper */
    public JacksonPlatformErrorCodec() {
        this(new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL));
    }

    /* ================= 写出 ================= */

    @Override
    public Map<String, Object> toProblemMap(PlatformError e) {
        Map<String, Object> m = new LinkedHashMap<>(12);

        // RFC7807 标准字段
        putIfNonEmpty(m, TYPE,     e.type() == null ? null : e.type().toString());
        putIfNonEmpty(m, TITLE,    emptyToNull(e.title()));
        // status 必须是数字
        if (e.status() > 0) m.put(STATUS, e.status());
        putIfNonEmpty(m, DETAIL,   emptyToNull(e.detail()));
        putIfNonEmpty(m, INSTANCE, emptyToNull(e.instance()));

        // 扩展字段
        putIfNonEmpty(m, CODE,     e.code() == null ? null : e.code().toString());
        putIfNonEmpty(m, SERVICE,  emptyToNull(e.service()));
        putIfNonEmpty(m, TRACE_ID, emptyToNull(e.traceId()));
        if (e.timestamp() != null) m.put(TIMESTAMP, e.timestamp().toString());
        if (e.extras() != null && !e.extras().isEmpty()) m.put(EXTRAS, e.extras());

        return m;
    }

    @Override
    public String toJson(PlatformError error) {
        try {
            return mapper.writeValueAsString(toProblemMap(error));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Serialize PlatformError failed", ex);
        }
    }

    @Override
    public byte[] toBytes(PlatformError error) {
        return toJson(error).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /* ================= 读入 ================= */

    @Override
    @SuppressWarnings("unchecked")
    public PlatformError fromJson(String json) {
        try {
            Map<String, Object> map = mapper.readValue(json, mapStringObjType);
            return fromProblemMap(map);
        } catch (JsonProcessingException ex) {
            // JSON 不是合法对象时，兜底输出一个最小错误
            return minimalFallback("COM-U0001",
                    "Invalid problem+json payload: " + shorten(json, 256), 500);
        }
    }

    @Override
    public PlatformError fromBytes(byte[] bytes) {
        return fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    @SuppressWarnings("unchecked")
    public PlatformError fromProblemMap(Map<String, ?> mapIn) {
        if (mapIn == null || mapIn.isEmpty()) {
            return minimalFallback("COM-U0001", "Empty problem body", 500);
        }
        // 宽松读取
        String codeLiteral = asString(mapIn.get(CODE));
        ErrorCode code;
        try {
            code = (codeLiteral != null) ? ErrorCode.of(codeLiteral) : ErrorCode.of("COM-U0001");
        } catch (IllegalArgumentException ex) {
            code = ErrorCode.of("COM-U0001");
        }

        PlatformError.Builder b = PlatformError.builder(code.toString());

        // 标准字段
        String title   = asString(mapIn.get(TITLE));
        String detail  = asString(mapIn.get(DETAIL));
        String typeStr = asString(mapIn.get(TYPE));
        String instStr = asString(mapIn.get(INSTANCE));
        Integer status = asInt(mapIn.get(STATUS));

        if (title  != null) b.title(title);
        if (detail != null) b.detail(detail);
        if (status != null && status > 0) b.status(status);
        if (typeStr != null && !typeStr.isBlank()) {
            try { b.type(URI.create(typeStr)); } catch (Exception ignored) {}
        }
        if (instStr != null) b.instance(instStr);

        // 扩展字段
        String service = asString(mapIn.get(SERVICE));
        String traceId = asString(mapIn.get(TRACE_ID));
        String tsStr   = asString(mapIn.get(TIMESTAMP));
        if (service != null) b.service(service);
        if (traceId != null) b.traceId(traceId);
        if (tsStr != null && !tsStr.isBlank()) {
            try { b.timestamp(Instant.parse(tsStr)); } catch (Exception ignored) {}
        }
        Object extras = mapIn.get(EXTRAS);
        if (extras instanceof Map<?,?> emap) {
            // 保持次序
            Map<String,Object> safe = new LinkedHashMap<>();
            for (var e : emap.entrySet()) {
                safe.put(String.valueOf(e.getKey()), e.getValue());
            }
            b.putAllExtras(safe);
        }

        // 若 status 未提供，则按照类别给默认建议
        PlatformError built = b.build();
        if (built.status() == 0) {
            int rec = ErrorSpec.recommendedHttpStatus(code.category());
            built = built.withStatus(rec);
        }
        return built;
    }

    /* ================= 工具 ================= */

    private static void putIfNonEmpty(Map<String,Object> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
    private static String asString(Object v) {
        if (v == null) return null;
        if (v instanceof String s) return s;
        if (v instanceof Number n) return n.toString();
        return String.valueOf(v);
    }
    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.valueOf(asString(v)); } catch (NumberFormatException e) { return null; }
    }
    private static String shorten(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    private static PlatformError minimalFallback(String codeLiteral, String title, int status) {
        PlatformError.Builder b = PlatformError.builder(codeLiteral).title(title).status(status);
        return b.build();
    }
}
