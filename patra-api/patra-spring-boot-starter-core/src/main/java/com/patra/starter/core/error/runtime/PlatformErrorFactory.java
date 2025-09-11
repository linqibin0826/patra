package com.patra.starter.core.error.runtime;

import cn.hutool.core.util.StrUtil;
import com.patra.common.error.core.ErrorCode;
import com.patra.common.error.core.ErrorDef;
import com.patra.common.error.core.ErrorSpec;
import com.patra.common.error.core.PlatformError;
import com.patra.starter.core.error.registry.Codebook;
import com.patra.starter.core.error.registry.CodebookEntry;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * 平台错误构建器工厂类，提供基于错误码册的统一错误构建能力。
 *
 * <p>主要职责：
 * <ul>
 *   <li>从 {@link ErrorDef} 或 {@link ErrorCode} 创建 {@link PlatformError.Builder}</li>
 *   <li>自动从 {@link Codebook} 补全错误标题、HTTP 状态码和扩展属性</li>
 *   <li>提供默认的 HTTP 状态码推荐机制</li>
 * </ul>
 *
 * <p>设计特点：
 * <ul>
 *   <li>与 Spring/Feign 无直接耦合，通过自动装配注入 Codebook</li>
 *   <li>支持流式构建模式，提供丰富的配置方法</li>
 *   <li>线程安全的静态工厂方法设计</li>
 * </ul>
 *
 * @author linqibin
 * @see ErrorDef
 * @see ErrorCode
 * @see PlatformError
 * @see Codebook
 * @since 0.1.0
 */
@Slf4j
public final class PlatformErrorFactory {

    /**
     * 私有构造器，防止实例化。
     */
    private PlatformErrorFactory() {
    }

    /* -------------------- Codebook 注入点 -------------------- */

    /**
     * 错误码册提供者接口，由自动装配在启动时注入。
     */
    public interface CodebookProvider {
        /**
         * 获取当前的错误码册实例。
         *
         * @return 错误码册实例
         */
        Codebook get();
    }

    private static volatile CodebookProvider codebookProvider;

    /**
     * 设置错误码册提供者。
     *
     * @param provider 错误码册提供者实例
     * @throws NullPointerException 如果提供者为 null
     */
    public static void setCodebookProvider(CodebookProvider provider) {
        codebookProvider = Objects.requireNonNull(provider, "CodebookProvider must not be null");
    }

    /**
     * 获取当前的错误码册实例。
     *
     * @return 当前错误码册实例，如果未设置则返回 null
     */
    private static Codebook getCurrentCodebook() {
        return codebookProvider == null ? null : codebookProvider.get();
    }

    /* -------------------- 入口构造重载 -------------------- */

    /**
     * 从错误定义创建构建器。
     *
     * @param errorDef 错误定义
     * @return 平台错误构建器
     * @throws NullPointerException 如果错误定义为 null
     */
    public static Builder of(ErrorDef errorDef) {
        Objects.requireNonNull(errorDef, "ErrorDef cannot be null");
        return of(errorDef.code());
    }

    /**
     * 从错误码创建构建器。
     *
     * @param errorCode 错误码
     * @return 平台错误构建器
     * @throws NullPointerException 如果错误码为 null
     */
    public static Builder of(ErrorCode errorCode) {
        Objects.requireNonNull(errorCode, "ErrorCode cannot be null");

        PlatformError.Builder delegate = PlatformError.builder(errorCode.toString());
        boolean httpStatusSet = false;

        // 尝试从错误码册补全信息
        Codebook codebook = getCurrentCodebook();
        if (codebook != null) {
            Optional<CodebookEntry> entryOpt = codebook.find(errorCode);
            if (entryOpt.isPresent()) {
                CodebookEntry entry = entryOpt.get();
                httpStatusSet = enrichBuilderFromCodebook(delegate, entry);
            }
        }

        return new Builder(errorCode, delegate, httpStatusSet);
    }

    /**
     * 从错误码字面量创建构建器（过渡/测试用，业务侧不建议直接使用）。
     *
     * @param errorCodeLiteral 错误码字面量
     * @return 平台错误构建器
     * @throws IllegalArgumentException 如果错误码字面量格式不正确
     */
    public static Builder of(String errorCodeLiteral) {
        return of(ErrorCode.of(errorCodeLiteral));
    }

    /**
     * 从错误码册条目丰富构建器内容。
     *
     * @param builder 平台错误构建器
     * @param entry   错误码册条目
     * @return 是否设置了 HTTP 状态码
     */
    private static boolean enrichBuilderFromCodebook(PlatformError.Builder builder, CodebookEntry entry) {
        boolean httpStatusSet = false;

        // 设置标题
        if (StrUtil.isNotBlank(entry.title())) {
            builder.title(entry.title());
        }

        // 设置 HTTP 状态码
        if (entry.httpStatus() != null && entry.httpStatus() > 0) {
            builder.status(entry.httpStatus());
            httpStatusSet = true;
        }

        // 设置扩展属性
        if (entry.extras() != null && !entry.extras().isEmpty()) {
            builder.putAllExtras(entry.extras());
        }

        return httpStatusSet;
    }

    /* -------------------- 流式构建器 -------------------- */

    /**
     * 平台错误流式构建器，提供丰富的配置方法。
     */
    public static final class Builder {
        private final ErrorCode errorCode;
        private final PlatformError.Builder delegate;
        private final Map<String, Object> extensionBuffer = new LinkedHashMap<>();
        private boolean httpStatusExplicitlySet;

        /**
         * 内部构造器。
         *
         * @param errorCode     错误码
         * @param delegate      委托的平台错误构建器
         * @param httpStatusSet HTTP 状态码是否已设置
         */
        private Builder(ErrorCode errorCode, PlatformError.Builder delegate, boolean httpStatusSet) {
            this.errorCode = errorCode;
            this.delegate = delegate;
            this.httpStatusExplicitlySet = httpStatusSet;
        }

        /**
         * 设置错误标题。
         *
         * @param title 错误标题
         * @return 构建器实例
         */
        public Builder title(String title) {
            delegate.title(title);
            return this;
        }

        /**
         * 设置错误详情。
         *
         * @param detail 错误详情
         * @return 构建器实例
         */
        public Builder detail(String detail) {
            delegate.detail(detail);
            return this;
        }

        /**
         * 设置 HTTP 状态码。
         *
         * @param status HTTP 状态码
         * @return 构建器实例
         */
        public Builder status(int status) {
            delegate.status(status);
            this.httpStatusExplicitlySet = true;
            return this;
        }

        /**
         * 设置资源实例路径。
         *
         * @param instance 资源实例路径
         * @return 构建器实例
         */
        public Builder instance(String instance) {
            delegate.instance(instance);
            return this;
        }

        /**
         * 设置错误类型 URI。
         *
         * @param type 错误类型 URI
         * @return 构建器实例
         */
        public Builder type(URI type) {
            delegate.type(type);
            return this;
        }

        /**
         * 设置服务名称。
         *
         * @param service 服务名称
         * @return 构建器实例
         */
        public Builder service(String service) {
            delegate.service(service);
            return this;
        }

        /**
         * 设置链路追踪 ID。
         *
         * @param traceId 链路追踪 ID
         * @return 构建器实例
         */
        public Builder traceId(String traceId) {
            delegate.traceId(traceId);
            return this;
        }

        /**
         * 设置错误发生时间戳。
         *
         * @param timestamp 错误发生时间戳
         * @return 构建器实例
         */
        public Builder timestamp(Instant timestamp) {
            delegate.timestamp(timestamp);
            return this;
        }

        /**
         * 设置详情最大长度。
         *
         * @param maxLength 最大长度
         * @return 构建器实例
         */
        public Builder maxDetailLength(int maxLength) {
            delegate.maxDetailLength(maxLength);
            return this;
        }

        /**
         * 添加扩展参数。
         *
         * @param key   参数键
         * @param value 参数值
         * @return 构建器实例
         */
        public Builder param(String key, Object value) {
            if (StrUtil.isNotBlank(key) && value != null) {
                extensionBuffer.put(key, value);
            }
            return this;
        }

        /**
         * 批量添加扩展参数。
         *
         * @param params 扩展参数映射
         * @return 构建器实例
         */
        public Builder params(Map<String, ?> params) {
            if (params != null && !params.isEmpty()) {
                params.forEach(this::param);
            }
            return this;
        }

        /**
         * 对扩展参数中的敏感信息进行脱敏处理。
         *
         * @param keys 需要脱敏的键名数组
         * @return 构建器实例
         */
        public Builder redactExtras(String... keys) {
            delegate.redactExtras(keys);
            return this;
        }

        /**
         * 构建最终的平台错误对象。
         *
         * @return 平台错误对象
         */
        public PlatformError build() {
            // 合并扩展参数缓冲区
            if (!extensionBuffer.isEmpty()) {
                delegate.putAllExtras(extensionBuffer);
            }

            // 如果没有显式设置 HTTP 状态码，则使用推荐状态码
            if (!this.httpStatusExplicitlySet) {
                int recommendedStatus = ErrorSpec.recommendedHttpStatus(errorCode.category());
                delegate.status(recommendedStatus);
            }

            return delegate.build();
        }
    }
}
