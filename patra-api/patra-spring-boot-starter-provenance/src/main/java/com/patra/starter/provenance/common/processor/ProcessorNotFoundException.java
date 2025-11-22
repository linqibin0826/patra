package com.patra.starter.provenance.common.processor;

/// Processor未找到异常
///
/// 当尝试获取不存在的DataProcessor时抛出此异常。
///
/// **使用场景**：
///
/// - ProcessorRegistry.getProcessor()找不到对应的Processor
///   - ProvenanceDataProvider查找Processor失败
///
/// **设计理念**：
///
/// - 继承RuntimeException，避免强制try-catch
///   - 提供详细的错误信息（包含DataType）
///   - 支持异常链（可选的cause参数）
///
/// @author linqibin
/// @since 0.1.0
public class ProcessorNotFoundException extends RuntimeException {

  /// 构造函数
  ///
  /// @param message 错误消息
  public ProcessorNotFoundException(String message) {
    super(message);
  }

  /// 构造函数（带原因）
  ///
  /// @param message 错误消息
  /// @param cause 原始异常
  public ProcessorNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
