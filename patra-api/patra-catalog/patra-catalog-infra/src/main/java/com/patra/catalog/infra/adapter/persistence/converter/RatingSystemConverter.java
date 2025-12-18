package com.patra.catalog.infra.adapter.persistence.converter;

import com.patra.catalog.domain.model.enums.RatingSystem;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/// RatingSystem 枚举的 JPA 转换器。
///
/// 将 `RatingSystem` 枚举与数据库 VARCHAR 字段进行双向转换。
/// 使用枚举的 `code` 字段作为持久化值（如 "JCR"、"CAS"、"SCOPUS"）。
///
/// @author linqibin
/// @since 0.1.0
@Converter(autoApply = true)
public class RatingSystemConverter implements AttributeConverter<RatingSystem, String> {

  /// 将枚举转换为数据库列值。
  ///
  /// @param attribute 枚举值
  /// @return 对应的代码值，null 输入返回 null
  @Override
  public String convertToDatabaseColumn(RatingSystem attribute) {
    return attribute != null ? attribute.getCode() : null;
  }

  /// 将数据库列值转换为枚举。
  ///
  /// @param dbData 数据库中的代码值
  /// @return 对应的枚举值，null 或空白输入返回 null
  @Override
  public RatingSystem convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return null;
    }
    return RatingSystem.fromCode(dbData);
  }
}
