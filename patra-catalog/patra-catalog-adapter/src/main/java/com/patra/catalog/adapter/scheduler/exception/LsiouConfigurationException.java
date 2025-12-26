package com.patra.catalog.adapter.scheduler.exception;

/// LSIOU 配置异常。
///
/// 当 LSIOU 数据源配置无效时抛出，例如：
/// - URL 格式无效
/// - 文件名不符合预期格式（无法推断版本号）
/// - 配置项缺失
///
/// 这是 Adapter 层的异常，用于表示配置/协议转换问题，
/// 与 Domain 层的业务规则违反异常区分开。
///
/// @author linqibin
/// @since 0.1.0
public class LsiouConfigurationException extends RuntimeException {

  /// 创建配置异常。
  ///
  /// @param message 错误消息
  public LsiouConfigurationException(String message) {
    super(message);
  }

  /// 创建带原因的配置异常。
  ///
  /// @param message 错误消息
  /// @param cause 原始异常
  public LsiouConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
