package dev.linqibin.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/// MeSH SCR（补充概念记录）JPA 实体，映射到表 `cat_mesh_scr`。
///
/// **表结构**：存储 NLM MeSH 补充概念记录，主要用于化学物质、药物协议、疾病等补充术语。
///
/// **数据规模**：初始 31.8 万条 / 年增长 2 万 / 5 年规模 42 万
///
/// **关键字段说明**：
///
/// - `ui` SCR 唯一标识符（格式：C000001-C999999）
/// - `name` 补充概念名称（化学物质名称可能很长）
/// - `scr_class` SCR 类别（1-6 六种类别）
/// - `active_status` 是否有效（false=已废弃，true=有效）
/// - `mesh_version` MeSH 版本年份（如 "2025"）
///
/// **索引说明**：
///
/// - 唯一索引 `uk_scr_ui`: ui 支持高频精确查询
/// - 普通索引 `idx_name`: name 前缀索引，支持按名称查询
/// - 普通索引 `idx_scr_class`: 支持按 SCR 类别筛选
/// - 复合索引 `idx_active_version`: 筛选某版本的有效 SCR
/// - 复合索引 `idx_revised_version`: 支持增量更新查询
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_scr",
    uniqueConstraints = {@UniqueConstraint(name = "uk_scr_ui", columnNames = "ui")},
    indexes = {
      @Index(name = "idx_name", columnList = "name(100)"),
      @Index(name = "idx_scr_class", columnList = "scr_class"),
      @Index(name = "idx_active_version", columnList = "active_status, mesh_version"),
      @Index(name = "idx_revised_version", columnList = "date_revised, mesh_version")
    })
public class MeshScrEntity extends BaseJpaEntity {

  /// SCR 唯一标识符（格式：C000001-C999999）
  @Column(name = "ui", nullable = false, length = 10)
  private String ui;

  /// 补充概念名称（化学物质名称可能很长）
  @Column(name = "name", nullable = false, length = 500)
  private String name;

  /// SCR 类别（1=化学物质，2=协议，3=疾病，4=生物，5=人口群体，6=其他）
  @Column(name = "scr_class", nullable = false)
  private Integer scrClass;

  /// 说明（用法说明和注释）
  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  /// 频率（文献中出现的频率描述）
  @Column(name = "frequency", length = 50)
  private String frequency;

  /// 之前的索引方式（历史参考）
  @Column(name = "previous_indexing", columnDefinition = "TEXT")
  private String previousIndexing;

  /// 创建日期
  @Column(name = "date_created")
  private LocalDate dateCreated;

  /// 修订日期（用于增量更新）
  @Column(name = "date_revised")
  private LocalDate dateRevised;

  /// 是否有效（false=已废弃，true=有效）
  @Column(name = "active_status", nullable = false)
  private boolean activeStatus;

  /// MeSH 版本年份（如 "2025"）
  @Column(name = "mesh_version", length = 10)
  private String meshVersion;

  /// 其他元数据（JSON 扩展字段）
  @Column(name = "metadata", columnDefinition = "JSON")
  private String metadata;
}
