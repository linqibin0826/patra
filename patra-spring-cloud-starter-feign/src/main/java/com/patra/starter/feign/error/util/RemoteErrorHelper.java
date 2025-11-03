package com.patra.starter.feign.error.util;

import com.patra.starter.feign.error.exception.RemoteCallException;

/**
 * 用于处理 {@link com.patra.starter.feign.error.exception.RemoteCallException} 的辅助工具类
 *
 * <p>提供常见 HTTP / 业务错误模式的语义检查,使适配器代码保持简洁和表达力。
 *
 * <p><b>支持的错误检查:</b>
 *
 * <ul>
 *   <li>HTTP 状态码检查(404, 409, 401, 403, 422, 429等)
 *   <li>业务错误码匹配(支持约定的后缀如 -0404)
 *   <li>可重试性判断(5xx, 429, 408, 503, 504)
 *   <li>客户端错误(4xx)和服务器错误(5xx)分类
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class RemoteErrorHelper {

  private RemoteErrorHelper() {}

  /** 判断错误是否表示未找到条件(HTTP 404 或错误码以 {@code -0404} 结尾) */
  public static boolean isNotFound(RemoteCallException ex) {
    return ex.getHttpStatus() == 404
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0404"));
  }

  /** 判断错误是否表示冲突(HTTP 409 或错误码以 {@code -0409} 结尾) */
  public static boolean isConflict(RemoteCallException ex) {
    return ex.getHttpStatus() == 409
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0409"));
  }

  /** 判断错误是否表示未授权请求(HTTP 401 或错误码以 {@code -0401} 结尾) */
  public static boolean isUnauthorized(RemoteCallException ex) {
    return ex.getHttpStatus() == 401
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0401"));
  }

  /** 判断错误是否表示禁止访问(HTTP 403 或错误码以 {@code -0403} 结尾) */
  public static boolean isForbidden(RemoteCallException ex) {
    return ex.getHttpStatus() == 403
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0403"));
  }

  /** 判断错误是否表示无法处理的实体(HTTP 422 或错误码以 {@code -0422} 结尾) */
  public static boolean isUnprocessableEntity(RemoteCallException ex) {
    return ex.getHttpStatus() == 422
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0422"));
  }

  /** 判断错误是否表示速率限制违规(HTTP 429 或错误码以 {@code -0429} 结尾) */
  public static boolean isTooManyRequests(RemoteCallException ex) {
    return ex.getHttpStatus() == 429
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0429"));
  }

  /**
   * 判断下游调用是否导致 4xx 状态码
   *
   * @return 如果是客户端错误则返回 {@code true}
   */
  public static boolean isClientError(RemoteCallException ex) {
    return ex.getHttpStatus() >= 400 && ex.getHttpStatus() < 500;
  }

  /**
   * 判断下游调用是否导致 5xx 状态码
   *
   * @return 如果是服务器错误则返回 {@code true}
   */
  public static boolean isServerError(RemoteCallException ex) {
    return ex.getHttpStatus() >= 500 && ex.getHttpStatus() < 600;
  }

  /** 检查下游业务错误码是否与提供的值匹配 */
  public static boolean is(RemoteCallException ex, String errorCode) {
    return errorCode != null && errorCode.equals(ex.getErrorCode());
  }

  /**
   * 检查是否存在下游业务错误码
   *
   * @return 如果存在业务错误码则返回 {@code true}
   */
  public static boolean hasErrorCode(RemoteCallException ex) {
    return ex.hasErrorCode();
  }

  /**
   * 检查是否存在下游跟踪标识符
   *
   * @return 如果存在跟踪标识符则返回 {@code true}
   */
  public static boolean hasTraceId(RemoteCallException ex) {
    return ex.hasTraceId();
  }

  /** 检查下游业务错误码是否为提供的值之一 */
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

  /**
   * 判断错误是否代表可能可重试的瞬时故障(如 5xx, 429, 408, 503, 504)
   *
   * @return 如果错误可重试则返回 {@code true}
   */
  public static boolean isRetryable(RemoteCallException ex) {
    int status = ex.getHttpStatus();
    return isServerError(ex)
        || status == 429
        || // Too Many Requests
        status == 408
        || // Request Timeout
        status == 503
        || // Service Unavailable
        status == 504; // Gateway Timeout
  }
}
