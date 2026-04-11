package com.patra.catalog.infra.persistence.converter.attribute;

import com.patra.catalog.domain.model.enums.CasWarningLevel;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/// `CasWarningLevel` 枚举的 JPA 转换器。
///
/// 将枚举与数据库 `VARCHAR` 字段双向转换，存储小写英文代码（`high`/`medium`/`low`），
/// 与 `PublicationIdentifierTypeConverter` 保持风格一致。
///
/// **容错策略**：数据库中未知代码值会返回 null 而非抛异常，避免因 LetPub 新增级别
/// 导致查询整表失败；未知值会被调用方当作"无预警级别信息"处理。
///
/// @author linqibin
/// @since 0.1.0
@Converter
public class CasWarningLevelConverter implements AttributeConverter<CasWarningLevel, String> {

  @Override
  public String convertToDatabaseColumn(CasWarningLevel attribute) {
    return attribute != null ? attribute.getCode() : null;
  }

  @Override
  public CasWarningLevel convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return null;
    }
    try {
      return CasWarningLevel.fromCode(dbData);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
