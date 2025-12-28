package com.patra.registry.domain.exception.dictionary;

import com.patra.registry.domain.exception.RegistryNotFound;

/// 来源标准未找到异常。
///
/// 当请求的来源标准未配置时抛出。
///
/// @author linqibin
/// @since 0.1.0
public class DictionaryStandardNotFoundException extends RegistryNotFound {

  /// 构造来源标准未找到异常。
  ///
  /// @param standardCode 来源标准代码
  public DictionaryStandardNotFoundException(String standardCode) {
    super("来源标准未找到: " + standardCode);
  }
}
