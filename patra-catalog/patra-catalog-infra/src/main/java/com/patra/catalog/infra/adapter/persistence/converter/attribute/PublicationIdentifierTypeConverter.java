package com.patra.catalog.infra.adapter.persistence.converter.attribute;

import com.patra.common.model.enums.PublicationIdentifierType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/// PublicationIdentifierType 枚举的 JPA 转换器。
///
/// 将 `PublicationIdentifierType` 枚举与数据库 VARCHAR 字段进行双向转换。
/// 使用枚举的 `code` 字段作为持久化值（如 "pmid"、"doi"、"pmc"）。
///
/// **转换规则**：
///
/// - 枚举 → 数据库：使用 `getCode()` 返回小写代码
/// - 数据库 → 枚举：使用 `fromCodeOrOther()` 安全解析，未知值返回 `OTHER`
///
/// @author linqibin
/// @since 0.1.0
@Converter
public class PublicationIdentifierTypeConverter
    implements AttributeConverter<PublicationIdentifierType, String> {

  /// 将枚举转换为数据库列值。
  ///
  /// @param attribute 枚举值
  /// @return 对应的代码值（如 "pmid"、"doi"），null 输入返回 null
  @Override
  public String convertToDatabaseColumn(PublicationIdentifierType attribute) {
    return attribute != null ? attribute.getCode() : null;
  }

  /// 将数据库列值转换为枚举。
  ///
  /// 使用 `fromCodeOrOther()` 安全解析，未知类型返回 `OTHER` 而非抛出异常。
  /// 这样即使数据库中存在新增的标识符类型，也不会导致查询失败。
  ///
  /// @param dbData 数据库中的代码值
  /// @return 对应的枚举值，null 或空白输入返回 null，未知类型返回 `OTHER`
  @Override
  public PublicationIdentifierType convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return null;
    }
    return PublicationIdentifierType.fromCodeOrOther(dbData);
  }
}
