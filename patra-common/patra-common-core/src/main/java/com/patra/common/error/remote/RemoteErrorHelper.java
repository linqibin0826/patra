package com.patra.common.error.remote;

/// 用于处理 {@link RemoteCallException} 的辅助工具类
///
/// 提供常见 HTTP / 业务错误模式的语义检查，使适配器代码保持简洁和表达力。
///
/// **支持的错误检查：**
///
/// - HTTP 状态码检查（404, 409, 401, 403, 422, 429 等）
/// - 业务错误码匹配（支持约定的后缀如 `-0404`）
/// - 可重试性判断（5xx, 429, 408, 503, 504）
/// - 客户端错误（4xx）和服务器错误（5xx）分类
///
/// **使用示例：**
/// ```java
/// try {
///     return provenanceEndpoint.getProvenance(code);
/// } catch (RemoteCallException ex) {
///     if (RemoteErrorHelper.isNotFound(ex)) {
///         throw new ProvenanceNotFoundException(code);
///     }
///     if (RemoteErrorHelper.isServerError(ex)) {
///         log.warn("下游服务错误，降级处理: {}", ex.getMessage());
///         return fallbackProvenance(code);
///     }
///     throw ex;
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class RemoteErrorHelper {

  /// 私有构造函数，防止实例化工具类
  private RemoteErrorHelper() {}

  /// 判断错误是否表示未找到条件（HTTP 404 或错误码以 `-0404` 结尾）
  ///
  /// @param ex 远程调用异常
  /// @return 如果是未找到错误则返回 true
  public static boolean isNotFound(RemoteCallException ex) {
    return ex.getHttpStatus() == 404
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0404"));
  }

  /// 判断错误是否表示冲突（HTTP 409 或错误码以 `-0409` 结尾）
  ///
  /// @param ex 远程调用异常
  /// @return 如果是冲突错误则返回 true
  public static boolean isConflict(RemoteCallException ex) {
    return ex.getHttpStatus() == 409
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0409"));
  }

  /// 判断错误是否表示未授权请求（HTTP 401 或错误码以 `-0401` 结尾）
  ///
  /// @param ex 远程调用异常
  /// @return 如果是未授权错误则返回 true
  public static boolean isUnauthorized(RemoteCallException ex) {
    return ex.getHttpStatus() == 401
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0401"));
  }

  /// 判断错误是否表示禁止访问（HTTP 403 或错误码以 `-0403` 结尾）
  ///
  /// @param ex 远程调用异常
  /// @return 如果是禁止访问错误则返回 true
  public static boolean isForbidden(RemoteCallException ex) {
    return ex.getHttpStatus() == 403
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0403"));
  }

  /// 判断错误是否表示无法处理的实体（HTTP 422 或错误码以 `-0422` 结尾）
  ///
  /// @param ex 远程调用异常
  /// @return 如果是无法处理实体错误则返回 true
  public static boolean isUnprocessableEntity(RemoteCallException ex) {
    return ex.getHttpStatus() == 422
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0422"));
  }

  /// 判断错误是否表示速率限制违规（HTTP 429 或错误码以 `-0429` 结尾）
  ///
  /// @param ex 远程调用异常
  /// @return 如果是速率限制错误则返回 true
  public static boolean isTooManyRequests(RemoteCallException ex) {
    return ex.getHttpStatus() == 429
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0429"));
  }

  /// 判断下游调用是否导致 4xx 状态码
  ///
  /// @param ex 远程调用异常
  /// @return 如果是客户端错误则返回 true
  public static boolean isClientError(RemoteCallException ex) {
    return ex.getHttpStatus() >= 400 && ex.getHttpStatus() < 500;
  }

  /// 判断下游调用是否导致 5xx 状态码
  ///
  /// @param ex 远程调用异常
  /// @return 如果是服务器错误则返回 true
  public static boolean isServerError(RemoteCallException ex) {
    return ex.getHttpStatus() >= 500 && ex.getHttpStatus() < 600;
  }

  /// 检查下游业务错误码是否与提供的值匹配
  ///
  /// @param ex 远程调用异常
  /// @param errorCode 期望的错误码
  /// @return 如果错误码匹配则返回 true
  public static boolean is(RemoteCallException ex, String errorCode) {
    return errorCode != null && errorCode.equals(ex.getErrorCode());
  }

  /// 检查是否存在下游业务错误码
  ///
  /// @param ex 远程调用异常
  /// @return 如果存在业务错误码则返回 true
  public static boolean hasErrorCode(RemoteCallException ex) {
    return ex.hasErrorCode();
  }

  /// 检查是否存在下游跟踪标识符
  ///
  /// @param ex 远程调用异常
  /// @return 如果存在跟踪标识符则返回 true
  public static boolean hasTraceId(RemoteCallException ex) {
    return ex.hasTraceId();
  }

  /// 检查下游业务错误码是否为提供的值之一
  ///
  /// @param ex 远程调用异常
  /// @param errorCodes 期望的错误码列表
  /// @return 如果错误码匹配任何一个则返回 true
  public static boolean isAnyOf(RemoteCallException ex, String... errorCodes) {
    if (errorCodes == null || errorCodes.length == 0) {
      return false;
    }

    String actualCode = ex.getErrorCode();
    if (actualCode == null) {
      return false;
    }

    for (String code : errorCodes) {
      if (actualCode.equals(code)) {
        return true;
      }
    }
    return false;
  }

  /// 判断错误是否代表可能可重试的瞬时故障（如 5xx, 429, 408, 503, 504）
  ///
  /// @param ex 远程调用异常
  /// @return 如果错误可重试则返回 true
  public static boolean isRetryable(RemoteCallException ex) {
    int status = ex.getHttpStatus();
    return isServerError(ex)
        || status == 429  // Too Many Requests
        || status == 408  // Request Timeout
        || status == 503  // Service Unavailable
        || status == 504; // Gateway Timeout
  }
}
