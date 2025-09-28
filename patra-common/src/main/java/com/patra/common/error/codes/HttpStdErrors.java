package com.patra.common.error.codes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP 对齐类（0xxx 段）标准错误码工厂。
 *
 * <p>动机：不同服务的错误码前缀不同（如 ING/REG），但 0xxx 与 HTTP 语义一一对应，
 * 因此提供一个通用工厂按前缀生成标准错误码对象，避免在各模块复制粘贴。</p>
 */
public final class HttpStdErrors {

    private HttpStdErrors() {}

    private static final Map<String, Group> CACHE = new ConcurrentHashMap<>();

    /**
     * 获取某个前缀（如 "ING"、"REG"）对应的一组标准 HTTP 错误码。
     *
     * @param prefix 错误码前缀，空或空白将回退为 "UNKNOWN"
     */
    public static Group of(String prefix) {
        String p = (prefix == null || prefix.isBlank()) ? "UNKNOWN" : prefix;
        return CACHE.computeIfAbsent(p, Group::new);
    }

    /**
     * 标准 HTTP 错误码集合（绑定指定前缀）。
     */
    public static final class Group {
        private final String prefix;
        private Group(String prefix) { this.prefix = prefix; }

        // 4xx
        public ErrorCodeLike BAD_REQUEST()     { return code("0400", 400); }
        public ErrorCodeLike UNAUTHORIZED()    { return code("0401", 401); }
        public ErrorCodeLike FORBIDDEN()       { return code("0403", 403); }
        public ErrorCodeLike NOT_FOUND()       { return code("0404", 404); }
        public ErrorCodeLike CONFLICT()        { return code("0409", 409); }
        public ErrorCodeLike UNPROCESSABLE()   { return code("0422", 422); }
        public ErrorCodeLike TOO_MANY()        { return code("0429", 429); }

        // 5xx
        public ErrorCodeLike INTERNAL_ERROR()  { return code("0500", 500); }
        public ErrorCodeLike UNAVAILABLE()     { return code("0503", 503); }
        public ErrorCodeLike GATEWAY_TIMEOUT() { return code("0504", 504); }

        private ErrorCodeLike code(String suffix, int status) {
            final String value = prefix + "-" + suffix;
            final int http = status;
            return new ErrorCodeLike() {
                @Override public String code() { return value; }
                @Override public int httpStatus() { return http; }
                @Override public String toString() { return value; }
            };
        }
    }
}
