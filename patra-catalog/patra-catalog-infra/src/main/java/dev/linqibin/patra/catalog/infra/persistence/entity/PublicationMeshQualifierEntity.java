package dev.linqibin.patra.catalog.infra.persistence.entity;

import dev.linqibin.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 文献-MeSH 限定词关联 JPA 实体，映射到表 `cat_publication_mesh_qualifier`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 管理文献 MeSH 标引中的限定词信息
/// - 使用 `qualifier_ui` 作为关联键（与项目其他 MeSH 关联表一致）
/// - 通过 `publication_mesh_heading_id` 关联到父表
///
/// **业务含义**：
///
/// MeSH 限定词（Qualifier）用于进一步细化主题词的含义。
/// 例如："Diabetes Mellitus"[主题词] + "drug therapy"[限定词] = "糖尿病药物治疗"
///
/// **索引设计**：
///
/// - `uk_heading_qualifier`：标引 ID + 限定词 UI 唯一索引
/// - `idx_heading`：标引索引（查询某个标引的所有限定词）
/// - `idx_qualifier_ui`：限定词 UI 索引（查询某个限定词的使用情况）
/// - `idx_major_topic`：主要主题索引
///
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "cat_publication_mesh_qualifier",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_heading_qualifier",
          columnNames = {"publication_mesh_heading_id", "qualifier_ui"})
    },
    indexes = {
      @Index(name = "idx_heading", columnList = "publication_mesh_heading_id"),
      @Index(name = "idx_qualifier_ui", columnList = "qualifier_ui"),
      @Index(name = "idx_major_topic", columnList = "is_major_topic")
    })
public class PublicationMeshQualifierEntity extends ValueObjectJpaEntity {

  // ========== 关联信息 ==========

  /// 文献 MeSH 标引 ID（外键：cat_publication_mesh_heading.id）。
  @Column(name = "publication_mesh_heading_id", nullable = false)
  private Long publicationMeshHeadingId;

  /// MeSH 限定词 UI（关联键：cat_mesh_qualifier.ui）。
  ///
  /// 存储如 "Q000379" 格式的唯一标识符。
  @Column(name = "qualifier_ui", nullable = false, length = 10)
  private String qualifierUi;

  // ========== 标引元数据 ==========

  /// 是否为文章的主要主题。
  ///
  /// 限定词也可以单独标记为 MajorTopic。
  @Column(name = "is_major_topic", nullable = false)
  @Builder.Default
  private Boolean majorTopic = false;

  /// 限定词顺序（可选）。
  ///
  /// 保留原始 XML 中的限定词顺序，在同一标引内的排序。
  @Column(name = "qualifier_order")
  private Integer qualifierOrder;

  // ========== 便捷方法 ==========

  /// 创建限定词记录。
  ///
  /// @param headingId 文献 MeSH 标引 ID
  /// @param qualifierUi MeSH 限定词 UI
  /// @param majorTopic 是否为主要主题
  /// @param qualifierOrder 限定词顺序
  /// @return 新建的实体
  public static PublicationMeshQualifierEntity of(
      Long headingId, String qualifierUi, boolean majorTopic, Integer qualifierOrder) {
    return PublicationMeshQualifierEntity.builder()
        .publicationMeshHeadingId(headingId)
        .qualifierUi(qualifierUi)
        .majorTopic(majorTopic)
        .qualifierOrder(qualifierOrder)
        .build();
  }
}
