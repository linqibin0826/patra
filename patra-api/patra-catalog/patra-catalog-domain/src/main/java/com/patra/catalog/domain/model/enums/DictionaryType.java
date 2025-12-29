package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 字典类型枚举。
///
/// 定义系统支持的所有字典类型，每个类型对应 patra-registry 中的 `sys_dict_type` 记录。
/// 用于在字典解析时指定要解析的字典类别。
///
/// **支持的字典类型**：
///
/// | 枚举值 | typeCode | 说明 | 使用场景 |
/// |--------|----------|------|---------|
/// | COUNTRY | country | 国家/地区 | PubMed 国家字段标准化 |
/// | LANGUAGE | language | 语言 | PubMed 语言代码转换 |
/// | SUBJECT | subject | 学科分类 | MeSH 主题词映射 |
///
/// **使用示例**：
///
/// ```java
/// // 在字典解析中使用
/// Map<String, String> result = dictionaryResolver.resolve(
///     DictionaryType.COUNTRY,
///     SourceStandard.NAME_EN,
///     rawValues
/// );
///
/// // 从字符串解析
/// DictionaryType type = DictionaryType.fromTypeCode("country");
///
/// // 类型判断
/// if (type.isCountry()) {
///     // 处理国家编码
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum DictionaryType {

  /// 国家/地区字典（ISO 3166-1 alpha-2 规范标准）。
  COUNTRY("country"),

  /// 语言字典（BCP 47 语言标签规范标准）。
  LANGUAGE("language"),

  /// 学科分类字典（内部统一分类规范标准）。
  SUBJECT("subject");

  /// 字典类型代码（对应 patra-registry 中的 sys_dict_type.type_code）。
  private final String typeCode;

  DictionaryType(String typeCode) {
    this.typeCode = typeCode;
  }

  // ==================== 解析方法 ====================

  /// 从字典类型代码解析枚举值。
  ///
  /// @param typeCode 字典类型代码（如 "country"、"language"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码为空或无法识别
  public static DictionaryType fromTypeCode(String typeCode) {
    Assert.notBlank(typeCode, "字典类型代码不能为空");
    for (DictionaryType type : values()) {
      if (type.typeCode.equals(typeCode)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的字典类型代码: " + typeCode);
  }

  /// 尝试从字典类型代码解析枚举值，无法识别时返回 null。
  ///
  /// @param typeCode 字典类型代码
  /// @return 对应的枚举值，无法识别则返回 null
  public static DictionaryType fromTypeCodeOrNull(String typeCode) {
    if (typeCode == null || typeCode.isBlank()) {
      return null;
    }
    for (DictionaryType type : values()) {
      if (type.typeCode.equals(typeCode)) {
        return type;
      }
    }
    return null;
  }

  // ==================== 类型判断方法 ====================

  /// 判断是否为国家字典类型。
  ///
  /// @return true 如果为国家字典
  public boolean isCountry() {
    return this == COUNTRY;
  }

  /// 判断是否为语言字典类型。
  ///
  /// @return true 如果为语言字典
  public boolean isLanguage() {
    return this == LANGUAGE;
  }

  /// 判断是否为学科分类字典类型。
  ///
  /// @return true 如果为学科分类字典
  public boolean isSubject() {
    return this == SUBJECT;
  }
}
