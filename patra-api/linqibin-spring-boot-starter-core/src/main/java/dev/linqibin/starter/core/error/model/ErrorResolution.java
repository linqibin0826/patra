package dev.linqibin.starter.core.error.model;

import dev.linqibin.commons.error.codes.ErrorCodeLike;

/// 异常解析结果,组合业务错误码、HTTP 状态码和解析策略。
///
/// 该记录类封装了错误解析引擎的输出,提供统一的错误表示格式。
///
/// @param errorCode 解析后的业务错误码(永不为 `null`)
/// @param httpStatus 解析后的 HTTP 状态码(范围: 100–599)
/// @param strategy 使用的解析策略（用于可观测性和调试）
/// @author linqibin
/// @since 0.1.0
public record ErrorResolution(
    ErrorCodeLike errorCode, int httpStatus, ResolutionStrategy strategy) {

  /// 规范构造器,强制执行错误解析结果的验证规则。
  ///
  /// 验证规则:
  ///
  /// - 错误码不能为 null
  ///   - HTTP 状态码必须在 100–599 之间
  ///   - 解析策略不能为 null
  ///
  /// @throws IllegalArgumentException 如果验证失败

  public ErrorResolution {
    if (errorCode == null) {
      throw new IllegalArgumentException("错误码不能为 null");
    }
    if (httpStatus < 100 || httpStatus > 599) {
      throw new IllegalArgumentException("HTTP 状态码必须在 100 到 599 之间,实际值: " + httpStatus);
    }
    if (strategy == null) {
      throw new IllegalArgumentException("解析策略不能为 null");
    }
  }
}
