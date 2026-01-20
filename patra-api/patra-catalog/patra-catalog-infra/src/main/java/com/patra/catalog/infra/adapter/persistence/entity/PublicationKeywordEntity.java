package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 文献关键词关联 JPA 实体，映射到表 `cat_publication_keyword`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 将 `KeywordSet.keywords` 扁平化存储，保留 `source` 区分来源
/// - 一篇文献可能有多个来源的关键词（author、publisher、indexer 等）
///
/// **业务含义**：
///
/// 关键词用于描述文章的核心主题，帮助检索和分类。PubMed 文献通常包含：
/// - 作者提供的关键词（author keywords）
/// - 出版商或索引机构添加的关键词
///
/// **索引设计**：
///
/// - `idx_publication`：出版物索引（查询某篇文献的所有关键词）
/// - `idx_source`：来源索引（按来源筛选）
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
    name = "cat_publication_keyword",
    indexes = {
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_source", columnList = "source"),
      @Index(name = "idx_major_topic", columnList = "is_major_topic")
    })
public class PublicationKeywordEntity extends ValueObjectJpaEntity {

  // ========== 关联信息 ==========

  /// 出版物 ID（外键：cat_publication.id）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  // ========== 关键词信息 ==========

  /// 关键词来源。
  ///
  /// 常见值：author（作者提供）, publisher（出版商）, editor（编辑）, indexer（索引机构）。
  @Column(name = "source", length = 50)
  private String source;

  /// 关键词文本。
  @Column(name = "term", nullable = false, length = 500)
  private String term;

  /// 是否为主要主题。
  @Column(name = "is_major_topic", nullable = false)
  @lombok.Builder.Default
  private Boolean majorTopic = false;

  /// 关键词顺序。
  @Column(name = "keyword_order")
  private Integer keywordOrder;

  // ========== 便捷方法 ==========

  /// 创建关键词记录。
  ///
  /// @param publicationId 出版物 ID
  /// @param source 关键词来源
  /// @param term 关键词文本
  /// @param majorTopic 是否为主要主题
  /// @param keywordOrder 顺序
  /// @return 新建的实体
  public static PublicationKeywordEntity of(
      Long publicationId, String source, String term, boolean majorTopic, Integer keywordOrder) {
    return PublicationKeywordEntity.builder()
        .publicationId(publicationId)
        .source(source)
        .term(term)
        .majorTopic(majorTopic)
        .keywordOrder(keywordOrder)
        .build();
  }
}
