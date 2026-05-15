package dev.linqibin.patra.registry.domain.exception.dictionary;

import dev.linqibin.patra.registry.domain.exception.RegistryRuleViolation;

/// 来源标准被禁用异常。
///
/// 当来源标准存在但被禁用时抛出。
///
/// @author linqibin
/// @since 0.1.0
public class DictionaryStandardDisabledException extends RegistryRuleViolation {

  /// 构造来源标准被禁用异常。
  ///
  /// @param standardCode 来源标准代码
  public DictionaryStandardDisabledException(String standardCode) {
    super("来源标准已禁用: " + standardCode);
  }
}
