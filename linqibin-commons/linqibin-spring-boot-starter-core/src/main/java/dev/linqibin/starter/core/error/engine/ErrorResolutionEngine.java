package dev.linqibin.starter.core.error.engine;

import dev.linqibin.starter.core.error.model.ErrorResolution;

/// 错误解析引擎接口,将任意异常转换为平台统一的错误表示。
///
/// 解析策略(按优先级):
///
/// @author linqibin
/// @since 0.1.0
/// @see dev.linqibin.starter.core.error.engine.DefaultErrorResolutionEngine
public interface ErrorResolutionEngine {

  /// 将提供的异常解析为结构化错误。
  ///
  /// @param exception 要解析的异常(永不为 `null`)
  /// @return 解析后的错误结果,包含错误码和 HTTP 状态码
  ErrorResolution resolve(Throwable exception);
}
