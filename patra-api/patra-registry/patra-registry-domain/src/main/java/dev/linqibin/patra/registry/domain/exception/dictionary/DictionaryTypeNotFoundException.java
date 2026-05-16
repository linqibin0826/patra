package dev.linqibin.patra.registry.domain.exception.dictionary;

import dev.linqibin.patra.registry.domain.exception.RegistryNotFound;

/// 字典类型未找到异常。
///
/// 当请求的字典类型不存在时抛出。
///
/// @author linqibin
/// @since 0.1.0
public class DictionaryTypeNotFoundException extends RegistryNotFound {

  /// 构造字典类型未找到异常。
  ///
  /// @param typeCode 字典类型代码
  public DictionaryTypeNotFoundException(String typeCode) {
    super("字典类型未找到: " + typeCode);
  }
}
