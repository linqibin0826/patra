package com.patra.error.core;

import com.patra.error.registry.Codebook;
import com.patra.error.registry.CodebookEntry;
import com.patra.error.spec.ErrorSpec;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * PlatformError 的工厂：从 ErrorDef / ErrorCode 出发，自动补全 title/http，
 * 并提供流式 API 构造 PlatformError。
 * <p>
 * 使用方式（推荐在应用启动时注入 CodebookProvider）：
 * Problems.setCodebookProvider(() -> loadedCodebook);
 * <p>
 * 业务侧示例（配合“只在枚举里写 code”的做法）：
 * var err = Problems.of(REGErrors.MISSING_PROVENANCE_ID)
 * .param("param", "provenanceId")
 * .detail("param 'provenanceId' is missing")
 * .build();
 */
public final class Problems {

    private Problems() {
    }

    /* -------------------- Codebook 注入点 -------------------- */

    /**
     * Codebook 提供方（上层在启动时设置一次；可返回不可变 Codebook）
     */
    public interface CodebookProvider {
        Codebook get();
    }

    private static volatile CodebookProvider provider;

    /**
     * 设置全局 CodebookProvider（建议在应用启动时设置一次）
     */
    public static void setCodebookProvider(CodebookProvider p) {
        provider = Objects.requireNonNull(p, "CodebookProvider must not be null");
    }

    /**
     * 获取当前 Codebook（可能为 null）
     */
    public static Codebook currentCodebook() {
        return provider == null ? null : provider.get();
    }

    /* -------------------- 入口构造重载 -------------------- */

    /**
     * 从强类型错误定义（枚举实现 ErrorDef）创建
     */
    public static Builder of(ErrorDef def) {
        Objects.requireNonNull(def, "def");
        return of(def.code());
    }

    /**
     * 从 ErrorCode 创建
     */
    public static Builder of(ErrorCode code) {
        Objects.requireNonNull(code, "code");
        // 先用 code 初始化底层 Builder
        PlatformError.Builder delegate = PlatformError.builder(code.toString());

        // 自动补全：优先 Codebook
        boolean httpSet = false;
        Codebook cb = currentCodebook();
        if (cb != null) {
            Optional<CodebookEntry> entry = cb.find(code);
            if (entry.isPresent()) {
                CodebookEntry e = entry.get();
                if (e.title() != null && !e.title().isBlank()) {
                    delegate.title(e.title());
                }
                if (e.httpStatus() != null && e.httpStatus() > 0) {
                    delegate.status(e.httpStatus());
                    httpSet = true;
                }
                if (e.extras() != null && !e.extras().isEmpty()) {
                    delegate.putAllExtras(e.extras());
                }
            }
        }
        return new Builder(code, delegate, httpSet);
    }

    /**
     * 从字面量（不推荐在业务侧使用；保留给过渡/测试）
     */
    public static Builder of(String literal) {
        return of(ErrorCode.of(literal));
    }

    /* -------------------- Fluent Builder 包装 -------------------- */

    public static final class Builder {
        private final ErrorCode code;
        private final PlatformError.Builder delegate;
        private final Map<String, Object> extrasBuf = new LinkedHashMap<>();

        /**
         * 是否已经设置过 HTTP（来自 codebook 或显式 status 调用）
         */
        private boolean httpSet;

        private Builder(ErrorCode code, PlatformError.Builder delegate, boolean httpSet) {
            this.code = code;
            this.delegate = delegate;
            this.httpSet = httpSet;
        }

        /* ==== 常用便捷项 ==== */

        /**
         * 文本标题（通常无需调用，Codebook 会补上；也可覆盖）
         */
        public Builder title(String title) {
            delegate.title(title);
            return this;
        }

        /**
         * 详细描述（建议写“发生了什么/期望什么/如何修复”）
         */
        public Builder detail(String detail) {
            delegate.detail(detail);
            return this;
        }

        /**
         * 覆写/指定 HTTP 状态码（若不指定，将按类别给推荐值）
         */
        public Builder status(int status) {
            delegate.status(status);
            this.httpSet = true;
            return this;
        }

        /**
         * 资源定位（如 URL 路径或业务标识）
         */
        public Builder instance(String instance) {
            delegate.instance(instance);
            return this;
        }

        /**
         * 文档链接（通常由上层 Starter 统一填充 /errors/{code}，也可手动指定）
         */
        public Builder type(URI type) {
            delegate.type(type);
            return this;
        }

        /**
         * 出错服务名（starter-web 会自动填入，一般无需显式设置）
         */
        public Builder service(String service) {
            delegate.service(service);
            return this;
        }

        /**
         * traceId（starter-web 会自动填入，一般无需显式设置）
         */
        public Builder traceId(String traceId) {
            delegate.traceId(traceId);
            return this;
        }

        /**
         * 指定时间（默认 Instant.now()）
         */
        public Builder timestamp(Instant ts) {
            delegate.timestamp(ts);
            return this;
        }

        /**
         * detail 最大长度（>0 生效；防止过长）
         */
        public Builder maxDetailLength(int max) {
            delegate.maxDetailLength(max);
            return this;
        }

        /* ==== 扩展上下文（extras）==== */

        /**
         * 添加扩展键值（会在输出 Problem JSON 的扩展字段里出现）
         */
        public Builder param(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                extrasBuf.put(key, value);
            }
            return this;
        }

        /**
         * 批量添加扩展键值
         */
        public Builder params(Map<String, ?> extras) {
            if (extras != null && !extras.isEmpty()) {
                extras.forEach(this::param);
            }
            return this;
        }

        /**
         * 按键名（大小写不敏感）对 extras 做敏感遮蔽（例如 token/password/secret）
         */
        public Builder redactExtras(String... keys) {
            delegate.redactExtras(keys);
            return this;
        }

        /* ==== 终结构建 ==== */

        /**
         * 构建不可变 PlatformError：若未设置 HTTP，则按类别给推荐值
         */
        public PlatformError build() {
            if (!extrasBuf.isEmpty()) {
                delegate.putAllExtras(extrasBuf);
            }
            if (!httpSet) {
                int rec = ErrorSpec.recommendedHttpStatus(code.category());
                // 这里不读取 delegate 的内部状态，直接按逻辑设置一次即可
                delegate.status(rec);
            }
            return delegate.build();
        }
    }
}
