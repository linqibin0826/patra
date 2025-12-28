package com.patra.registry.infra.adapter.persistence.entity.reference;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 来源标准 JPA 实体，映射到表 `sys_reference_standard`。
///
/// 表示外部值遵循的格式或规范。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "sys_reference_standard")
public class ReferenceStandardEntity extends BaseJpaEntity {

  /// 标准代码(例如 `ISO_3166_1_ALPHA2`、`GLOBAL`)。
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

  /// 是否启用。
  @Column(name = "enabled")
  private Boolean enabled;
}
