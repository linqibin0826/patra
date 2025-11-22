package com.patra.registry.domain.model.read.expr;

import com.patra.registry.domain.exception.DomainValidationException;

/// 统一表达式字段查询视图。
/// 
/// 用于从字典查询表达式字段定义的读优化投影。包含字段键、显示名称、数据类型、基数等元数据。
/// 
/// @author linqibin
/// @since 0.1.0
public record ExprFieldQuery(
    String fieldKey,
    String displayName,
    String description,
    String dataTypeCode,
    String cardinalityCode,
    boolean exposable,
    boolean dateField) {
  public ExprFieldQuery {
    fieldKey = DomainValidationException.notBlank(fieldKey, "Field key");
    dataTypeCode = DomainValidationException.notBlank(dataTypeCode, "Data type code");
    cardinalityCode = DomainValidationException.notBlank(cardinalityCode, "Cardinality code");
    displayName = displayName != null ? displayName.trim() : "";
    description = description != null ? description.trim() : "";
  }
}
