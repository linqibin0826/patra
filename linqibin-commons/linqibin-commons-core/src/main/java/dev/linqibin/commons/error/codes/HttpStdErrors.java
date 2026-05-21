package dev.linqibin.commons.error.codes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// HTTP 对齐的标准错误码工厂(0xxx 段)。
///
/// 每个服务拥有独特的前缀(例如,ING 或 REG),而 0xxx 后缀直接映射到 HTTP 语义。 此工厂生成带前缀的错误码对象,以避免模块间的重复。
///
/// @author linqibin
/// @since 0.1.0
public final class HttpStdErrors {

  /// 私有构造函数,防止实例化工具类。
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

    /// 创建错误码组。
    ///
    /// @param prefix 错误码前缀
    Group(String prefix) {
      this.prefix = prefix;
    }

    /// 获取 400 Bad Request 错误码。
    ///
    /// @return BAD_REQUEST 错误码
    public ErrorCodeLike BAD_REQUEST() {
      return code("0400", 400);
    }

    /// 获取 401 Unauthorized 错误码。
    ///
    /// @return UNAUTHORIZED 错误码
    public ErrorCodeLike UNAUTHORIZED() {
      return code("0401", 401);
    }

    /// 获取 403 Forbidden 错误码。
    ///
    /// @return FORBIDDEN 错误码
    public ErrorCodeLike FORBIDDEN() {
      return code("0403", 403);
    }

    /// 获取 404 Not Found 错误码。
    ///
    /// @return NOT_FOUND 错误码
    public ErrorCodeLike NOT_FOUND() {
      return code("0404", 404);
    }

    /// 获取 409 Conflict 错误码。
    ///
    /// @return CONFLICT 错误码
    public ErrorCodeLike CONFLICT() {
      return code("0409", 409);
    }

    /// 获取 422 Unprocessable Entity 错误码。
    ///
    /// @return UNPROCESSABLE 错误码
    public ErrorCodeLike UNPROCESSABLE() {
      return code("0422", 422);
    }

    /// 获取 429 Too Many Requests 错误码。
    ///
    /// @return TOO_MANY 错误码
    public ErrorCodeLike TOO_MANY() {
      return code("0429", 429);
    }

    /// 获取 500 Internal Server Error 错误码。
    ///
    /// @return INTERNAL_ERROR 错误码
    public ErrorCodeLike INTERNAL_ERROR() {
      return code("0500", 500);
    }

    /// 获取 503 Service Unavailable 错误码。
    ///
    /// @return UNAVAILABLE 错误码
    public ErrorCodeLike UNAVAILABLE() {
      return code("0503", 503);
    }

    /// 获取 504 Gateway Timeout 错误码。
    ///
    /// @return GATEWAY_TIMEOUT 错误码
    public ErrorCodeLike GATEWAY_TIMEOUT() {
      return code("0504", 504);
    }

    /// 创建带前缀的错误码实例。
    ///
    /// @param suffix 错误码后缀(例如 "0400")
    /// @param status HTTP 状态码
    /// @return 错误码实例
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
