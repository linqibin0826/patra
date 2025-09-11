package com.patra.starter.core.error.codec;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.MapType;
import com.patra.common.error.core.ErrorCode;
import com.patra.common.error.core.ErrorSpec;
import com.patra.common.error.core.PlatformError;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.patra.starter.core.error.codec.ProblemJsonConstant.*;

/**
 * 基于 Jackson 的平台错误编解码器实现。
 *
 * <p>主要特性：
 * <ul>
 *   <li>遵循 RFC7807 Problem Details 标准</li>
 *   <li>支持平台扩展字段（code、service、traceId 等）</li>
 *   <li>写出时手工构建 Map，确保时间/URI 按文本格式输出</li>
 *   <li>读入时宽松解析，容忍字段缺失和类型不匹配</li>
 *   <li>避免 JSR310 时间模块绑定，保证序列化格式一致性</li>
 * </ul>
 *
 * <p>配置建议：
 * <ul>
 *   <li>FAIL_ON_UNKNOWN_PROPERTIES = false（容忍未知字段）</li>
 *   <li>SerializationInclusion = NON_NULL（忽略 null 值）</li>
 *   <li>启用宽松的反序列化策略</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper();
 * PlatformErrorCodec codec = new JacksonPlatformErrorCodec(mapper);
 *
 * PlatformError error = PlatformError.builder("REG-C0101")
 *     .title("Parameter missing")
 *     .status(422)
 *     .build();
 *
 * String json = codec.toJson(error);
 * PlatformError decoded = codec.fromJson(json);
 * }</pre>
 *
 * @author linqibin
 * @see PlatformErrorCodec
 * @see ProblemJsonConstant
 * @since 0.1.0
 */
@Slf4j
public class JacksonPlatformErrorCodec implements PlatformErrorCodec {

    private final ObjectMapper objectMapper;
    private final MapType mapStringObjectType;

    /**
     * 使用指定的 ObjectMapper 构造编解码器。
     *
     * <p>构造器会自动配置 ObjectMapper 的关键属性：
     * <ul>
     *   <li>禁用 FAIL_ON_UNKNOWN_PROPERTIES，容忍未知字段</li>
     *   <li>设置 SerializationInclusion 为 NON_NULL，忽略空值</li>
     * </ul>
     *
     * @param mapper Jackson ObjectMapper 实例，不能为 null
     * @throws NullPointerException 如果 mapper 为 null
     */
    public JacksonPlatformErrorCodec(ObjectMapper mapper) {
        this.objectMapper = Objects.requireNonNull(mapper, "ObjectMapper must not be null");
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.mapStringObjectType = this.objectMapper.getTypeFactory()
                .constructMapType(Map.class, String.class, Object.class);
    }

    /**
     * 使用默认配置的 ObjectMapper 构造编解码器。
     *
     * <p>默认配置包括：
     * <ul>
     *   <li>禁用未知属性失败机制</li>
     *   <li>序列化时忽略 null 值</li>
     * </ul>
     */
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
        putIfNonEmpty(m, TYPE, e.type() == null ? null : e.type().toString());
        putIfNonEmpty(m, TITLE, e.title());
        // status 必须是数字
        if (e.status() > 0) m.put(STATUS, e.status());
        putIfNonEmpty(m, DETAIL, e.detail());
        putIfNonEmpty(m, INSTANCE, e.instance());

        // 扩展字段
        putIfNonEmpty(m, CODE, e.code() == null ? null : e.code().toString());
        putIfNonEmpty(m, SERVICE, e.service());
        putIfNonEmpty(m, TRACE_ID, e.traceId());
        if (e.timestamp() != null) m.put(TIMESTAMP, e.timestamp().toString());
        if (e.extras() != null && !e.extras().isEmpty()) m.put(EXTRAS, e.extras());

        return m;
    }

    @Override
    public String toJson(PlatformError error) {
        try {
            return objectMapper.writeValueAsString(toProblemMap(error));
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
    public PlatformError fromJson(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, mapStringObjectType);
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
        String title = asString(mapIn.get(TITLE));
        String detail = asString(mapIn.get(DETAIL));
        String typeStr = asString(mapIn.get(TYPE));
        String instStr = asString(mapIn.get(INSTANCE));
        Integer status = asInt(mapIn.get(STATUS));

        if (title != null) b.title(title);
        if (detail != null) b.detail(detail);
        if (status != null && status > 0) b.status(status);
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                b.type(URI.create(typeStr));
            } catch (Exception ignored) {
            }
        }
        if (instStr != null) b.instance(instStr);

        // 扩展字段
        String service = asString(mapIn.get(SERVICE));
        String traceId = asString(mapIn.get(TRACE_ID));
        String tsStr = asString(mapIn.get(TIMESTAMP));
        if (service != null) b.service(service);
        if (traceId != null) b.traceId(traceId);
        if (tsStr != null && !tsStr.isBlank()) {
            try {
                b.timestamp(Instant.parse(tsStr));
            } catch (Exception ignored) {
            }
        }
        Object extras = mapIn.get(EXTRAS);
        if (extras instanceof Map<?, ?> emap) {
            // 保持次序
            Map<String, Object> safe = new LinkedHashMap<>();
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

    /* ==================== 私有工具方法 ==================== */

    /**
     * 当值非空且非空白时才放入 Map。
     *
     * @param map   目标 Map
     * @param key   键
     * @param value 值
     */
    private static void putIfNonEmpty(Map<String, Object> map, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            map.put(key, value);
        }
    }

    /**
     * 安全地转换对象为字符串。
     *
     * @param value 待转换的对象
     * @return 字符串表示，如果为 null 则返回 null
     */
    private static String asString(Object value) {
        return Convert.toStr(value, null);
    }

    /**
     * 安全地转换对象为整数。
     *
     * @param value 待转换的对象
     * @return 整数值，转换失败时返回 null
     */
    private static Integer asInt(Object value) {
        return Convert.toInt(value, null);
    }

    /**
     * 截短字符串到指定长度。
     *
     * @param text      原字符串
     * @param maxLength 最大长度
     * @return 截短后的字符串，超长时会添加省略号
     */
    private static String shorten(String text, int maxLength) {
        return StrUtil.maxLength(text, maxLength);
    }

    /**
     * 创建兜底的最小错误对象。
     *
     * @param codeLiteral 错误码字面量
     * @param title       错误标题
     * @param status      HTTP 状态码
     * @return 平台错误对象
     */
    private static PlatformError minimalFallback(String codeLiteral, String title, int status) {
        return PlatformError.builder(codeLiteral)
                .title(title)
                .status(status)
                .build();
    }
}
