package com.patra.registry.infra.adapter.persistence.entity.reference;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 来源标准 JPA 实体，映射到表 `sys_reference_standard`。
///
/// 表示外部值遵循的格式或规范，以及该标准是否为特定字典类型的规范标准。
///
/// 每个字典类型只能有一个规范标准（`isCanonical = true`），
/// 规范标准定义了 `sys_dict_item.item_code` 应遵循的格式。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "sys_reference_standard")
public class ReferenceStandardEntity extends BaseJpaEntity {

  /// 所属字典类型代码（例如 `country`、`language`）。
  ///
  /// 与 `sys_dict_type.type_code` 逻辑关联。
  @Column(name = "dict_type_code", nullable = false, length = 64)
  private String dictTypeCode;

  /// 标准代码（例如 `ISO_3166_1_ALPHA2`、`NAME_EN`）。
  @Column(name = "standard_code", nullable = false, length = 64)
  private String standardCode;

  /// 标准名称。
  @Column(name = "standard_name", nullable = false, length = 200)
  private String standardName;

  /// 标准描述。
  @Column(name = "description", length = 500)
  private String description;

  /// 显示顺序。
  @Column(name = "display_order")
  private Integer displayOrder;

  /// 是否为该字典类型的规范标准。
  ///
  /// 规范标准决定了 `sys_dict_item.item_code` 的格式。
  /// 每个字典类型只能有一个规范标准（通过数据库生成列约束保证）。
  @Column(name = "is_canonical", nullable = false)
  private Boolean canonical;

  /// 是否启用。
  @Column(name = "enabled")
  private Boolean enabled;
}
