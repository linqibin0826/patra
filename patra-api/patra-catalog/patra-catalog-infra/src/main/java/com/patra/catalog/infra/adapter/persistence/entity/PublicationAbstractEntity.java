package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/// 文献摘要 JPA 实体，映射到表 `cat_publication_abstract`。
///
/// **表结构**：独立存储文献摘要（大文本），与文献主表为 1:1 关系。
///
/// **关键字段说明**：
///
/// - `publication_id` 文献 ID，外键关联 cat_publication.id（唯一约束）
/// - `plain_text` 纯文本摘要
/// - `structured_sections` 结构化摘要（JSON 对象）
/// - `abstract_type` 摘要类型：structured/unstructured/graphical/none
///
/// **索引说明**：
///
/// - 唯一索引 `uk_publication`: publication_id 保证一对一关系
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_publication_abstract",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_publication",
          columnNames = {"publication_id"})
    })
public class PublicationAbstractEntity extends ValueObjectJpaEntity {

  /// 文献 ID（外键：cat_publication.id，一对一关系）
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 纯文本摘要
  @Lob
  @Column(name = "plain_text", columnDefinition = "TEXT")
  private String plainText;

  /// 结构化摘要段落（JSON 对象）
  @Column(name = "structured_sections", columnDefinition = "JSON")
  private String structuredSections;

  /// 版权信息
  @Column(name = "copyright", length = 1000)
  private String copyright;

  /// 摘要类型：structured/unstructured/graphical/none
  @Column(name = "abstract_type", length = 32)
  private String abstractType;
}
