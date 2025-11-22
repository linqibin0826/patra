package com.patra.common.error.codes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// HTTP 对齐的标准错误码工厂(0xxx 段)。
///
/// 每个服务拥有独特的前缀(例如,ING 或 REG),而 0xxx 后缀直接映射到 HTTP 语义。 此工厂生成带前缀的错误码对象,以避免模块间的重复。
public final class HttpStdErrors {

  private HttpStdErrors() {}

  private static final Map<String, Group> CACHE = new ConcurrentHashMap<>();

  /// 返回给定前缀的标准 HTTP 错误码组的延迟缓存实例。
  ///
  /// @param prefix 错误码前缀(例如,`ING` 或 `REG`);空值回退到 `UNKNOWN`
  /// @return 带前缀的标准 HTTP 错误码组
  public static Group of(String prefix) {
    String p = (prefix == null || prefix.isBlank()) ? "UNKNOWN" : prefix;
    return CACHE.computeIfAbsent(p, Group::new);
  }

  /// 绑定到特定前缀的标准 HTTP 错误码组。
  public static class Group {
    private final String prefix;

    Group(String prefix) {
      this.prefix = prefix;
    }

    // 4xx
    public ErrorCodeLike BAD_REQUEST() {
      return code("0400", 400);
    }

    public ErrorCodeLike UNAUTHORIZED() {
      return code("0401", 401);
    }

    public ErrorCodeLike FORBIDDEN() {
      return code("0403", 403);
    }

    public ErrorCodeLike NOT_FOUND() {
      return code("0404", 404);
    }

    public ErrorCodeLike CONFLICT() {
      return code("0409", 409);
    }

    public ErrorCodeLike UNPROCESSABLE() {
      return code("0422", 422);
    }

    public ErrorCodeLike TOO_MANY() {
      return code("0429", 429);
    }

    // 5xx
    public ErrorCodeLike INTERNAL_ERROR() {
      return code("0500", 500);
    }

    public ErrorCodeLike UNAVAILABLE() {
      return code("0503", 503);
    }

    public ErrorCodeLike GATEWAY_TIMEOUT() {
      return code("0504", 504);
    }

    private ErrorCodeLike code(String suffix, int status) {
      final String value = prefix + "-" + suffix;
      final int http = status;
      return new ErrorCodeLike() {
        @Override
        public String code() {
          return value;
        }

        @Override
        public int httpStatus() {
          return http;
        }

        @Override
        public String toString() {
          return value;
        }
      };
    }
  }
}
