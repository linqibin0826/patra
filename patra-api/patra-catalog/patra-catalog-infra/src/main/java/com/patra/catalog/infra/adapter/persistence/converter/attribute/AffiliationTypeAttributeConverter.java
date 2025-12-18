package com.patra.catalog.infra.adapter.persistence.converter.attribute;

import com.patra.catalog.domain.model.enums.AffiliationType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/// AffiliationType 枚举的 JPA 转换器。
///
/// 将 `AffiliationType` 枚举与数据库中的 `code` 字符串进行双向转换。
///
/// @author linqibin
/// @since 0.1.0
@Converter(autoApply = true)
public class AffiliationTypeAttributeConverter implements AttributeConverter<AffiliationType, String> {

  @Override
  public String convertToDatabaseColumn(AffiliationType attribute) {
    return attribute != null ? attribute.getCode() : null;
  }

  @Override
  public AffiliationType convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return null;
    }
    return AffiliationType.fromCode(dbData);
  }
}
