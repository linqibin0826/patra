package dev.linqibin.patra.catalog.infra.persistence.entity;

import dev.linqibin.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/// 载体 MeSH 主题词 JPA 实体，映射到表 `cat_venue_mesh`。
///
/// **表结构**：存储期刊的 MeSH 主题词分类信息，来源于 NLM Serfile。
///
/// **关键字段说明**：
///
/// - `venue_id` 载体 ID，外键关联 cat_venue.id
/// - `descriptor_name` MeSH 描述符名称，如 "Medicine", "Cardiology"
/// - `descriptor_ui` MeSH 描述符唯一标识符，格式 D000001
/// - `is_major_topic` 是否主要主题，用于检索权重
/// - `qualifier_name` 限定符名称，如 "methods", "diagnosis"
/// - `qualifier_ui` 限定符唯一标识符，格式 Q000001
///
/// **索引说明**：
///
/// - 唯一索引 `uk_venue_mesh`: (venue_id, descriptor_name) 防止重复
/// - 普通索引 `idx_venue_id`: venue_id 支持查询期刊的所有主题词
/// - 普通索引 `idx_descriptor_ui`: descriptor_ui 支持按 MeSH ID 反查期刊
///
/// **使用场景**：
///
/// 1. 从 Serfile 导入期刊的 MeshHeadingList 数据
/// 2. 按学科分类检索期刊
/// 3. 期刊主题分析和推荐
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_venue_mesh",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_venue_mesh",
          columnNames = {"venue_id", "descriptor_name"})
    },
    indexes = {
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_descriptor_ui", columnList = "descriptor_ui")
    })
public class VenueMeshEntity extends ValueObjectJpaEntity {

  /// 载体 ID（外键：cat_venue.id）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  /// MeSH 描述符名称
  @Column(name = "descriptor_name", nullable = false, length = 255)
  private String descriptorName;

  /// MeSH 描述符唯一标识符（格式：D000001）
  @Column(name = "descriptor_ui", length = 20)
  private String descriptorUi;

  /// 是否主要主题（0=否，1=是）
  @Column(name = "is_major_topic")
  private Boolean isMajorTopic;

  /// MeSH 限定符名称
  @Column(name = "qualifier_name", length = 100)
  private String qualifierName;

  /// MeSH 限定符唯一标识符（格式：Q000001）
  @Column(name = "qualifier_ui", length = 20)
  private String qualifierUi;
}
