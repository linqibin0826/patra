package com.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// SCR 来源 JPA 实体，映射到表 `cat_mesh_scr_source`。
///
/// **表结构**：存储 SCR 的数据来源（如 NCI/FDA/OMIM/DrugBank 等），SCR 特有。
///
/// **数据规模**：初始 50 万 / 年增长 3 万 / 5 年规模 65 万（每个 SCR 平均 1.5 个来源）
///
/// **关键字段说明**：
///
/// - `scr_ui` SCR UI（关联 cat_mesh_scr.ui）
/// - `source` 来源（如 NCI2004_11_17/FDA SRS (2023)/OMIM (2013) 等）
/// - `order_num` 排序号（在同一 SCR 内的顺序）
///
/// **索引说明**：
///
/// - 普通索引 `idx_scr_ui`: 支持查询某 SCR 的所有来源
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_scr_source",
    indexes = {@Index(name = "idx_scr_ui", columnList = "scr_ui")})
public class MeshScrSourceEntity extends ValueObjectJpaEntity {

  /// SCR UI（关联：cat_mesh_scr.ui，格式：C000001）
  @Column(name = "scr_ui", nullable = false, length = 10)
  private String scrUi;

  /// 来源（如 NCI2004_11_17/FDA SRS (2023)/OMIM (2013) 等）
  @Column(name = "source", nullable = false, length = 500)
  private String source;

  /// 排序号（在同一 SCR 内的顺序）
  @Column(name = "order_num")
  private Integer orderNum;
}
