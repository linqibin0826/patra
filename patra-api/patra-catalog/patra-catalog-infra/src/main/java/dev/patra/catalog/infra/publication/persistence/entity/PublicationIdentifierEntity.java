package dev.patra.catalog.infra.publication.persistence.entity;

import dev.patra.catalog.domain.kernel.model.enums.PublicationIdentifierType;
import dev.patra.catalog.infra.publication.persistence.converter.PublicationIdentifierTypeConverter;
import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 文献标识符 JPA 实体，映射到表 `cat_publication_identifier`。
///
/// **表结构**：存储文献的各类标识符，支持一对多关系。
///
/// **关键字段说明**：
///
/// - `publication_id` 文献 ID，外键关联 cat_publication.id
/// - `type` 标识符类型：pmid/doi/pmc/pii/arxiv 等
/// - `value` 标识符值
/// - `source` 标识符来源（PubMed/Crossref/Manual）
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "cat_publication_identifier")
public class PublicationIdentifierEntity extends ValueObjectJpaEntity {

  /// 文献 ID（外键：cat_publication.id）
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 标识符类型：PMID/DOI/PMC/PII/ARXIV 等
  @Column(name = "type", nullable = false, length = 20)
  @Convert(converter = PublicationIdentifierTypeConverter.class)
  private PublicationIdentifierType type;

  /// 标识符值
  @Column(name = "identifier_value", nullable = false, length = 255)
  private String value;

  /// 标识符来源（PubMed/Crossref/Manual）
  @Column(name = "source", length = 50)
  private String source;
}
