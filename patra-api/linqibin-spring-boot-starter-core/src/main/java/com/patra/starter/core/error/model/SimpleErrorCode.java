package com.patra.starter.core.error.model;

import dev.linqibin.commons.error.codes.ErrorCodeLike;

/// {@link ErrorCodeLike} 的简单不可变实现。
///
/// 此类提供可复用的错误码表示,避免在代码库中重复创建匿名 ErrorCodeLike 实例。
///
/// 错误码格式: `{contextPrefix}-{httpStatus}`,例如:
///
/// - REG-0404 (注册服务,资源未找到)
///   - INGEST-0500 (采集服务,内部服务器错误)
///
/// @author linqibin
/// @since 0.1.0
public final class SimpleErrorCode implements ErrorCodeLike {

  private final String code;
  private final int httpStatus;

  /// 私有构造函数,防止直接实例化。
  ///
  /// @param code 错误码字符串
  /// @param httpStatus HTTP 状态码

  private SimpleErrorCode(String code, int httpStatus) {
    this.code = code;
    this.httpStatus = httpStatus;
  }

  /// 从上下文前缀和 HTTP 状态码后缀创建错误码。
  ///
  /// 生成的错误码格式: `{contextPrefix}-{suffix}`
  ///
  /// @param contextPrefix 上下文前缀(例如 "REG"、"INGEST"),如果为空则使用 "UNKNOWN"
  /// @param suffix HTTP 状态码后缀(例如 "0404"、"0500")
  /// @return 新创建的错误码实例

  public static SimpleErrorCode create(String contextPrefix, String suffix) {
    String prefix = (contextPrefix == null || contextPrefix.isBlank()) ? "UNKNOWN" : contextPrefix;
    String fullCode = prefix + "-" + suffix;
    int status = parseHttpStatus(suffix);
    return new SimpleErrorCode(fullCode, status);
  }

  /// 从后缀解析 HTTP 状态码,如果无效则默认为 500。
  ///
  /// @param suffix 状态码后缀
  /// @return HTTP 状态码(范围 100-599),如果无效则返回 500

  private static int parseHttpStatus(String suffix) {
    try {
      int status = Integer.parseInt(suffix);
      return (status >= 100 && status <= 599) ? status : 500;
    } catch (NumberFormatException e) {
      return 500;
    }
  }

  /// 获取错误码字符串。
  ///
  /// @return 错误码
  @Override
  public String code() {
    return code;
  }

  /// 获取 HTTP 状态码。
  ///
  /// @return HTTP 状态码
  @Override
  public int httpStatus() {
    return httpStatus;
  }

  /// 返回错误码的字符串表示。
  ///
  /// @return 错误码字符串
  @Override
  public String toString() {
    return code;
  }
}
