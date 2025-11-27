package com.patra.catalog.infra.adapter.parser;

/// XML 解析异常。
///
/// 用于包装 XML 解析过程中发生的技术异常（如 {@link javax.xml.stream.XMLStreamException}、
/// {@link java.io.IOException} 等），提供统一的异常类型便于上层处理。
///
/// **使用场景**：
/// - 创建 XMLStreamReader 失败
/// - 解析 XML 元素时发生错误
/// - 打开 XML 文件失败
///
/// **设计决策**：
/// - 继承 RuntimeException：XML 解析失败通常是不可恢复的技术异常
/// - 保留原始异常链：通过 cause 保留底层异常的堆栈信息
/// - 位于 infra 层：属于基础设施技术异常，不应泄漏到 domain 层
///
/// @author linqibin
/// @since 0.1.0
public class XmlParsingException extends RuntimeException {

  /// 使用消息和原因创建异常。
  ///
  /// @param message 描述异常的消息
  /// @param cause 原始异常（如 XMLStreamException、IOException）
  public XmlParsingException(String message, Throwable cause) {
    super(message, cause);
  }

  /// 使用消息创建异常。
  ///
  /// @param message 描述异常的消息
  public XmlParsingException(String message) {
    super(message);
  }
}
