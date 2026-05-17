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

/// 文献-MeSH 主题标引关联 JPA 实体，映射到表 `cat_publication_mesh_heading`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 管理文献与 MeSH 主题词（Descriptor）的多对多关系
/// - 使用 `descriptor_ui` 作为关联键（与项目其他 MeSH 关联表一致）
/// - 限定词（Qualifier）信息由 `PublicationMeshQualifierEntity` 独立管理
///
/// **业务含义**：
///
/// MeSH（Medical Subject Headings）是 NLM 创建的受控词表，用于标引医学文献。
/// 每篇文献可能有多个 MeSH 标引，每个标引包含一个主题词和可选的限定词列表。
///
/// **索引设计**：
///
/// - `uk_pub_descriptor`：出版物 ID + 主题词 UI 唯一索引
/// - `idx_publication`：出版物索引（查询某篇文献的所有 MeSH 标引）
/// - `idx_descriptor_ui`：主题词 UI 索引（查询某个主题词关联的所有文献）
/// - `idx_major_topic`：主要主题索引（筛选主要主题的标引）
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
    name = "cat_publication_mesh_heading",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_pub_descriptor",
          columnNames = {"publication_id", "descriptor_ui"})
    },
    indexes = {
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_descriptor_ui", columnList = "descriptor_ui"),
      @Index(name = "idx_major_topic", columnList = "is_major_topic")
    })
public class PublicationMeshHeadingEntity extends ValueObjectJpaEntity {

  // ========== 关联信息 ==========

  /// 出版物 ID（外键：cat_publication.id）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// MeSH 主题词 UI（关联键：cat_mesh_descriptor.ui）。
  ///
  /// 存储如 "D000001" 格式的唯一标识符。
  @Column(name = "descriptor_ui", nullable = false, length = 10)
  private String descriptorUi;

  // ========== 标引元数据 ==========

  /// 是否为文章的主要主题。
  ///
  /// PubMed 中标记为 MajorTopic="Y" 的主题词表示文章的核心主题。
  @Column(name = "is_major_topic", nullable = false)
  @Builder.Default
  private Boolean majorTopic = false;

  /// 标引顺序（可选）。
  ///
  /// 保留原始 XML 中的 MeSH 标引顺序，通常按主题重要性排列。
  @Column(name = "heading_order")
  private Integer headingOrder;

  // ========== 便捷方法 ==========

  /// 创建标引记录。
  ///
  /// @param publicationId 出版物 ID
  /// @param descriptorUi MeSH 主题词 UI
  /// @param majorTopic 是否为主要主题
  /// @param headingOrder 标引顺序
  /// @return 新建的实体
  public static PublicationMeshHeadingEntity of(
      Long publicationId, String descriptorUi, boolean majorTopic, Integer headingOrder) {
    return PublicationMeshHeadingEntity.builder()
        .publicationId(publicationId)
        .descriptorUi(descriptorUi)
        .majorTopic(majorTopic)
        .headingOrder(headingOrder)
        .build();
  }

  /// 标记为主要主题。
  public void markAsMajorTopic() {
    this.majorTopic = true;
  }
}
