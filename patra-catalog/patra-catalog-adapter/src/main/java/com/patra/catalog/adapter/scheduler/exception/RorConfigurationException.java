package com.patra.catalog.adapter.scheduler.exception;

/// ROR 配置异常。
///
/// 当 ROR 数据源配置无效时抛出（如 URL 为空、格式错误、无法解析版本号等）。
///
/// @author linqibin
/// @since 0.1.0
public class RorConfigurationException extends RuntimeException {

  @java.io.Serial private static final long serialVersionUID = 1L;

  /// 创建 ROR 配置异常。
  ///
  /// @param message 异常消息
  public RorConfigurationException(String message) {
    super(message);
  }

  /// 创建 ROR 配置异常。
  ///
  /// @param message 异常消息
  /// @param cause 原因异常
  public RorConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
