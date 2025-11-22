package com.patra.registry.api.dto.dict;

/// 需要验证的字典引用。
/// 
/// 字段说明:
/// 
/// @author linqibin
/// @since 0.1.0
public record DictionaryReferenceReq(String typeCode, String itemCode) {
  /// 规范构造器,执行非空/空白验证并修剪空格。
/// 
/// @param typeCode 被引用的字典类型标识符
/// @param itemCode 被引用的字典项标识符
  public DictionaryReferenceReq {
    if (typeCode == null || typeCode.trim().isEmpty()) {
      throw new IllegalArgumentException("字典类型代码不能为null或空字符串");
    }
    if (itemCode == null || itemCode.trim().isEmpty()) {
      throw new IllegalArgumentException("字典项代码不能为null或空字符串");
    }
    typeCode = typeCode.trim();
    itemCode = itemCode.trim();
  }
}
