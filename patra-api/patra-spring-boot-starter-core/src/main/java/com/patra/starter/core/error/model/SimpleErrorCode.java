package com.patra.starter.core.error.model;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * {@link ErrorCodeLike} 的简单不可变实现。
 *
 * <p>此类提供可复用的错误码表示,避免在代码库中重复创建匿名 ErrorCodeLike 实例。
 *
 * <p>错误码格式: {@code {contextPrefix}-{httpStatus}},例如:
 *
 * <ul>
 *   <li>REG-0404 (注册服务,资源未找到)
 *   <li>INGEST-0500 (采集服务,内部服务器错误)
 * </ul>
 *
 * @author Patra Team
 * @since 2.0
 */
public final class SimpleErrorCode implements ErrorCodeLike {

  private final String code;
  private final int httpStatus;

  private SimpleErrorCode(String code, int httpStatus) {
    this.code = code;
    this.httpStatus = httpStatus;
  }

  /**
   * 从上下文前缀和 HTTP 状态码后缀创建错误码。
   *
   * <p>生成的错误码格式: {@code {contextPrefix}-{suffix}}
   *
   * @param contextPrefix 上下文前缀(例如 "REG"、"INGEST"),如果为空则使用 "UNKNOWN"
   * @param suffix HTTP 状态码后缀(例如 "0404"、"0500")
   * @return 新创建的错误码实例
   */
  public static SimpleErrorCode create(String contextPrefix, String suffix) {
    String prefix = (contextPrefix == null || contextPrefix.isBlank()) ? "UNKNOWN" : contextPrefix;
    String fullCode = prefix + "-" + suffix;
    int status = parseHttpStatus(suffix);
    return new SimpleErrorCode(fullCode, status);
  }

  /**
   * 从后缀解析 HTTP 状态码,如果无效则默认为 500。
   *
   * @param suffix 状态码后缀
   * @return HTTP 状态码(范围 100-599),如果无效则返回 500
   */
  private static int parseHttpStatus(String suffix) {
    try {
      int status = Integer.parseInt(suffix);
      return (status >= 100 && status <= 599) ? status : 500;
    } catch (NumberFormatException e) {
      return 500;
    }
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int httpStatus() {
    return httpStatus;
  }

  @Override
  public String toString() {
    return code;
  }
}
