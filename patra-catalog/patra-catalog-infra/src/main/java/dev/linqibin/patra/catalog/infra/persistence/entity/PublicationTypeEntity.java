package dev.linqibin.patra.catalog.infra.persistence.entity;

import dev.linqibin.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 文献出版类型关联 JPA 实体，映射到表 `cat_publication_type`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 存储文献的出版类型信息（可能来自不同受控词表）
/// - 一篇文献可能有多个类型标签
///
/// **业务含义**：
///
/// 出版类型用于分类文献，常见类型：
/// - Journal Article（期刊文章）
/// - Review（综述）
/// - Meta-Analysis（荟萃分析）
/// - Clinical Trial（临床试验）
/// - Case Report（病例报告）
/// - Letter（通讯）
/// - Editorial（社论）
///
/// **索引设计**：
///
/// - `uk_pub_type`：出版物 ID + 类型值 + 词表来源 唯一索引（避免重复）
/// - `idx_publication`：出版物索引
/// - `idx_type_value`：类型值索引（按类型检索）
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
    name = "cat_publication_type",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_pub_type",
          columnNames = {"publication_id", "type_value", "vocabulary_source"})
    },
    indexes = {
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_type_value", columnList = "type_value")
    })
public class PublicationTypeEntity extends ValueObjectJpaEntity {

  // ========== 关联信息 ==========

  /// 出版物 ID（外键：cat_publication.id）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  // ========== 类型信息 ==========

  /// 发表类型的唯一标识符（来自受控词表，可为空）。
  @Column(name = "type_id", length = 50)
  private String typeId;

  /// 发表类型的文本描述（如 "Journal Article", "Review"）。
  @Column(name = "type_value", nullable = false, length = 200)
  private String typeValue;

  /// 词表来源（如 "MeSH", "Crossref"）。
  @Column(name = "vocabulary_source", length = 50)
  private String vocabularySource;

  /// 类型顺序。
  @Column(name = "type_order")
  private Integer typeOrder;

  // ========== 便捷方法 ==========

  /// 创建出版类型记录。
  ///
  /// @param publicationId 出版物 ID
  /// @param typeId 类型标识符
  /// @param typeValue 类型文本描述
  /// @param vocabularySource 词表来源
  /// @param typeOrder 顺序
  /// @return 新建的实体
  public static PublicationTypeEntity of(
      Long publicationId,
      String typeId,
      String typeValue,
      String vocabularySource,
      Integer typeOrder) {
    return PublicationTypeEntity.builder()
        .publicationId(publicationId)
        .typeId(typeId)
        .typeValue(typeValue)
        .vocabularySource(vocabularySource)
        .typeOrder(typeOrder)
        .build();
  }
}
