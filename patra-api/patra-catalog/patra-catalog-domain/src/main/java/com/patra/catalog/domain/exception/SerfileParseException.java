package com.patra.catalog.domain.exception;

import com.patra.common.error.trait.StandardErrorTrait;

/// Serfile XML 解析异常。
///
/// 当解析 NLM Serfile XML 文件失败时抛出此异常。
/// 携带 `RULE_VIOLATION` 特征表示输入数据不符合规范。
///
/// 可能的原因包括：
///
/// - XML 格式不符合 DTD 规范
/// - 必填字段缺失
/// - 文件编码问题
/// - I/O 错误
///
/// @author linqibin
/// @since 0.1.0
public class SerfileParseException extends CatalogException {

  /// 创建解析异常。
  ///
  /// @param message 错误消息
  public SerfileParseException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }

  /// 创建带原因的解析异常。
  ///
  /// @param message 错误消息
  /// @param cause 原因
  public SerfileParseException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.RULE_VIOLATION);
  }
}
