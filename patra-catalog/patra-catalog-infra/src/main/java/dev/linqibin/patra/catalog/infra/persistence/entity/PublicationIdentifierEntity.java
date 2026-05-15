package dev.linqibin.patra.catalog.infra.persistence.entity;

import dev.linqibin.patra.catalog.infra.persistence.converter.attribute.PublicationIdentifierTypeConverter;
import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import dev.linqibin.patra.common.model.enums.PublicationIdentifierType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
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
/// **索引说明**：
///
/// - 复合索引 `idx_pub_type`: (publication_id, type) 支持查询某文献的某类型标识符
/// - 复合索引 `idx_type_value`: (type, value) 支持按标识符反查文献
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_publication_identifier",
    indexes = {
      @Index(name = "idx_pub_type", columnList = "publication_id, type"),
      @Index(name = "idx_type_value", columnList = "type, value")
    })
public class PublicationIdentifierEntity extends ValueObjectJpaEntity {

  /// 文献 ID（外键：cat_publication.id）
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 标识符类型：PMID/DOI/PMC/PII/ARXIV 等
  @Column(name = "type", nullable = false, length = 20)
  @Convert(converter = PublicationIdentifierTypeConverter.class)
  private PublicationIdentifierType type;

  /// 标识符值
  @Column(name = "value", nullable = false, length = 255)
  private String value;

  /// 标识符来源（PubMed/Crossref/Manual）
  @Column(name = "source", length = 50)
  private String source;
}
