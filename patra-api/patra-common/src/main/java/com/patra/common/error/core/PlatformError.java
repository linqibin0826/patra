package com.patra.common.error.core;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * 平台错误聚合根，与 RFC7807 字段对齐（但不依赖任何 Web 框架）。
 * <p>
 * 字段语义：
 * - code（必填）：平台错误码值对象
 * - title/detail：面向人类可读的摘要/详情（注意敏感信息脱敏）
 * - status：HTTP 建议状态码（0 表示未设置）
 * - service：出错服务名
 * - traceId：链路追踪 ID
 * - timestamp：错误产生时间（UTC）
 * - instance：资源定位（如 URL 路径或标识）
 * - type：文档链接 URI（如 /errors/{code}）
 * - extras：扩展上下文（键值对，输出时可做脱敏/裁剪）
 * <p>
 * 设计要点：
 * - 对外暴露不可变视图（所有集合都做防御性复制 + unmodifiable）
 * - 提供 Builder 构建 + 合并扩展上下文的便捷方法
 * - 提供 detail/extras 的“安全裁剪/脱敏”工具，便于上层 Starter 直接复用
 */
public final class PlatformError {

    private final ErrorCode code;
    private final String title;
    private final String detail;
    private final int status;            // 0 表示“未设置”
    private final String service;
    private final String traceId;
    private final Instant timestamp;
    private final String instance;
    private final URI type;
    private final Map<String, Object> extras;

    private PlatformError(Builder b) {
        this.code = Objects.requireNonNull(b.code, "code must not be null");
        this.title = nullToEmpty(b.title);
        this.detail = safeClip(b.detail, b.maxDetailLength);
        this.status = b.status;
        this.service = nullToEmpty(b.service);
        this.traceId = nullToEmpty(b.traceId);
        this.timestamp = Objects.requireNonNullElseGet(b.timestamp, Instant::now);
        this.instance = nullToEmpty(b.instance);
        this.type = b.type;
        this.extras = Collections.unmodifiableMap(new LinkedHashMap<>(b.extras));
    }

    // ---------- 工厂 ----------
    public static Builder builder(ErrorCode code) {
        return new Builder(code);
    }

    public static Builder builder(String code) {
        return new Builder(ErrorCode.of(code));
    }

    // ---------- 读取器 ----------
    public ErrorCode code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    public int status() {
        return status;
    }

    public String service() {
        return service;
    }

    public String traceId() {
        return traceId;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String instance() {
        return instance;
    }

    public URI type() {
        return type;
    }

    public Map<String, Object> extras() {
        return extras;
    }

    // ---------- 衍生操作 ----------

    /**
     * 基于当前对象复制并合并 extras（后写覆盖先写），返回新对象
     */
    public PlatformError withExtras(Map<String, ?> more) {
        if (more == null || more.isEmpty()) return this;
        Builder b = toBuilder().putAllExtras(more);
        return b.build();
    }

    /**
     * 基于当前对象复制并修改 detail（会应用同样的长度裁剪）
     */
    public PlatformError withDetail(String newDetail) {
        return toBuilder().detail(newDetail).build();
    }

    /**
     * 基于当前对象复制并设置建议的 HTTP status
     */
    public PlatformError withStatus(int newStatus) {
        return toBuilder().status(newStatus).build();
    }

    /**
     * 生成可变 Builder（用于少量字段调整）
     */
    public Builder toBuilder() {
        Builder b = new Builder(this.code);
        b.title(this.title)
                .detail(this.detail)
                .status(this.status)
                .service(this.service)
                .traceId(this.traceId)
                .timestamp(this.timestamp)
                .instance(this.instance)
                .type(this.type)
                .putAllExtras(this.extras);
        return b;
    }

    // ---------- 工具 ----------

    /**
     * 对 detail 做最大长度裁剪，maxLen<=0 时不裁剪
     */
    private static String safeClip(String s, int maxLen) {
        if (s == null) return "";
        if (maxLen > 0 && s.length() > maxLen) {
            return s.substring(0, maxLen) + "…";
        }
        return s;
    }

    private static String nullToEmpty(String s) {
        return (s == null) ? "" : s;
    }

    // ---------- Builder ----------

    public static final class Builder {
        private final ErrorCode code;
        private String title;
        private String detail;
        private int status; // 默认 0：未设置
        private String service;
        private String traceId;
        private Instant timestamp;
        private String instance;
        private URI type;
        private final Map<String, Object> extras = new LinkedHashMap<>();
        private int maxDetailLength = 4000; // 安全默认：防止日志/响应过长

        private Builder(ErrorCode code) {
            this.code = Objects.requireNonNull(code, "code must not be null");
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder instance(String instance) {
            this.instance = instance;
            return this;
        }

        public Builder type(URI type) {
            this.type = type;
            return this;
        }

        /**
         * 设定 detail 最大长度（<=0 表示不裁剪）
         */
        public Builder maxDetailLength(int maxDetailLength) {
            this.maxDetailLength = maxDetailLength;
            return this;
        }

        /**
         * 添加单个扩展键值（null 值将被忽略）
         */
        public Builder putExtra(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                this.extras.put(key, value);
            }
            return this;
        }

        /**
         * 一次性合并扩展上下文（null 值会被忽略）
         */
        public Builder putAllExtras(Map<String, ?> map) {
            if (map == null || map.isEmpty()) return this;
            for (var e : map.entrySet()) {
                putExtra(e.getKey(), e.getValue());
            }
            return this;
        }

        /**
         * 对 extras 中的敏感键进行遮蔽（如 token/password/secret 等）
         *
         * @param keys 大小写不敏感匹配
         */
        public Builder redactExtras(String... keys) {
            if (keys == null || keys.length == 0) return this;
            Set<String> match = new HashSet<>();
            for (String k : keys) {
                if (k != null) match.add(k.toLowerCase());
            }
            for (var entry : new ArrayList<>(extras.entrySet())) {
                if (entry.getKey() != null && match.contains(entry.getKey().toLowerCase())) {
                    extras.put(entry.getKey(), "******");
                }
            }
            return this;
        }

        public PlatformError build() {
            return new PlatformError(this);
        }
    }
}
