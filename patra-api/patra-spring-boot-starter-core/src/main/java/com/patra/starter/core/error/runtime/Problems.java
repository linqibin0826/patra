package com.patra.starter.core.error.runtime;

import com.patra.common.error.core.ErrorCode;
import com.patra.common.error.core.ErrorDef;
import com.patra.common.error.core.ErrorSpec;
import com.patra.common.error.core.PlatformError;
import com.patra.starter.core.error.registry.Codebook;
import com.patra.starter.core.error.registry.CodebookEntry;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * PlatformError 工厂（放在 starter-core）：
 * - 从 ErrorDef/ErrorCode 生成 PlatformError.Builder
 * - 自动从 Codebook 补全 title/http/extras；若未命中则按类别给推荐 HTTP
 * - 与 Spring/Feign 无直接耦合，starter 的自动装配会注入 Codebook
 */
public final class Problems {

    private Problems() {}

    /* -------------------- Codebook 注入点 -------------------- */

    /** 由自动装配在启动时注入一次 */
    public interface CodebookProvider { Codebook get(); }
    private static volatile CodebookProvider provider;

    public static void setCodebookProvider(CodebookProvider p) {
        provider = Objects.requireNonNull(p, "CodebookProvider must not be null");
    }

    private static Codebook currentCodebook() {
        return provider == null ? null : provider.get();
    }

    /* -------------------- 入口构造重载 -------------------- */

    public static Builder of(ErrorDef def) {
        Objects.requireNonNull(def, "def");
        return of(def.code());
    }

    public static Builder of(ErrorCode code) {
        Objects.requireNonNull(code, "code");
        PlatformError.Builder delegate = PlatformError.builder(code.toString());

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

    /** 过渡/测试用（业务侧不建议直接用字面量） */
    public static Builder of(String literal) {
        return of(ErrorCode.of(literal));
    }

    /* -------------------- Fluent Builder -------------------- */

    public static final class Builder {
        private final ErrorCode code;
        private final PlatformError.Builder delegate;
        private final Map<String, Object> extrasBuf = new LinkedHashMap<>();
        private boolean httpSet;

        private Builder(ErrorCode code, PlatformError.Builder delegate, boolean httpSet) {
            this.code = code;
            this.delegate = delegate;
            this.httpSet = httpSet;
        }

        public Builder title(String title) { delegate.title(title); return this; }
        public Builder detail(String detail) { delegate.detail(detail); return this; }
        public Builder status(int status) { delegate.status(status); this.httpSet = true; return this; }
        public Builder instance(String instance) { delegate.instance(instance); return this; }
        public Builder type(URI type) { delegate.type(type); return this; }
        public Builder service(String service) { delegate.service(service); return this; }
        public Builder traceId(String traceId) { delegate.traceId(traceId); return this; }
        public Builder timestamp(Instant ts) { delegate.timestamp(ts); return this; }
        public Builder maxDetailLength(int max) { delegate.maxDetailLength(max); return this; }

        public Builder param(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                extrasBuf.put(key, value);
            }
            return this;
        }
        public Builder params(Map<String, ?> extras) {
            if (extras != null && !extras.isEmpty()) extras.forEach(this::param);
            return this;
        }
        public Builder redactExtras(String... keys) {
            delegate.redactExtras(keys);
            return this;
        }

        public PlatformError build() {
            if (!extrasBuf.isEmpty()) {
                delegate.putAllExtras(extrasBuf);
            }
            if (!this.httpSet) {
                int rec = ErrorSpec.recommendedHttpStatus(code.category());
                delegate.status(rec);
            }
            return delegate.build();
        }
    }
}
