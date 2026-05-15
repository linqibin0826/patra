package com.patra.catalog.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// XML 解析异常（领域层）。
///
/// 当解析 XML 文件（如 NLM Serfile、MeSH Descriptor/Qualifier）失败时抛出此异常。
/// 携带 `RULE_VIOLATION` 特征表示输入数据不符合预期格式。
///
/// **适用场景**：
///
/// - XML 格式不符合 DTD/XSD 规范
/// - 必填元素或属性缺失
/// - 文件编码问题
/// - I/O 读取错误
///
/// **设计决策**：
///
/// - 定义在 Domain 层，确保 Application 层可以捕获而不依赖 Infrastructure
/// - 使用 `RULE_VIOLATION` 语义特征，表示输入数据违反了预期规则
/// - 作为通用 XML 解析异常，适用于所有 XML Parser 适配器
///
/// @author linqibin
/// @since 0.1.0
public class XmlParseException extends CatalogException {

  /// 创建 XML 解析异常。
  ///
  /// @param message 错误消息
  public XmlParseException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }

  /// 创建带原因的 XML 解析异常。
  ///
  /// @param message 错误消息
  /// @param cause 原始异常（如 XMLStreamException、IOException）
  public XmlParseException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.RULE_VIOLATION);
  }
}
