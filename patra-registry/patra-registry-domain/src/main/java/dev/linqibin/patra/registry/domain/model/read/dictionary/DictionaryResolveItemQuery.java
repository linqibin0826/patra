package dev.linqibin.patra.registry.domain.model.read.dictionary;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;

/// 字典解析项查询视图。
///
/// 记录单个原始值的解析结果与状态。
///
/// @author linqibin
/// @since 0.1.0
public record DictionaryResolveItemQuery(
    String rawValue, String resolvedCode, String resolvedName, DictionaryResolveStatus status) {

  /// 带验证的规范构造函数。
  ///
  /// @param rawValue 原始输入值
  /// @param resolvedCode 解析后的字典项代码
  /// @param resolvedName 解析后的字典项名称
  /// @param status 解析状态
  public DictionaryResolveItemQuery {
    rawValue = DomainValidationException.trimOrNull(rawValue);
    resolvedCode = DomainValidationException.trimOrNull(resolvedCode);
    resolvedName = DomainValidationException.trimOrNull(resolvedName);
    status = DomainValidationException.nonNull(status, "Dictionary resolve status");
  }
}
