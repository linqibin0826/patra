package com.patra.registry.infra.adapter.persistence.entity.expr;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 表达式字段字典 JPA 实体，映射到表 `reg_expr_field_dict`。
///
/// 定义跨数据源共享的规范字段元数据。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_expr_field_dict")
public class ExprFieldDictEntity extends ValueObjectJpaEntity {

  /// 规范字段键，在各环境间保持稳定。
  @Column(name = "field_key", nullable = false, length = 50)
  private String fieldKey;

  /// 可选的显示名称，在控制台中展示。
  @Column(name = "display_name", length = 100)
  private String displayName;

  /// 详细描述，解释字段语义。
  @Column(name = "description", length = 500)
  private String description;

  /// 数据类型代码 (DATE/DATETIME/NUMBER/TEXT/...)。
  @Column(name = "data_type_code", length = 20)
  private String dataTypeCode;

  /// 基数代码，指示单值或多值字段。
  @Column(name = "cardinality_code", length = 20)
  private String cardinalityCode;

  /// 指示字段是否可以暴露给客户端。
  @Column(name = "exposable")
  private Boolean exposable;

  /// 指示字段是否应被视为日期类型。
  @Column(name = "is_date")
  private Boolean dateField;
}
