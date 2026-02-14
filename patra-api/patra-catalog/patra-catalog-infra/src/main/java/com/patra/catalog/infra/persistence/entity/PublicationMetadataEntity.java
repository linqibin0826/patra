package com.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/// 文献元数据 JPA 实体，映射到表 `cat_publication_metadata`。
///
/// **表结构**：独立管理文献的元数据信息（索引状态、质量评分、数据溯源），与文献主表为 1:1 关系。
///
/// **关键字段说明**：
///
/// - `publication_id` 文献 ID，外键关联 cat_publication.id（唯一约束）
/// - `indexing_status` 索引状态：Pending/Indexed/MEDLINE 等
/// - `quality_score/completeness_score` 质量/完整性评分：A/B/C/D/F
/// - `data_source` 数据来源：PubMed/EPMC/Crossref 等
///
/// **索引说明**：
///
/// - 唯一索引 `uk_pub_metadata`: publication_id 保证一对一关系
/// - 普通索引支持按索引状态、数据来源、导入批次、审核状态查询
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_publication_metadata",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_pub_metadata",
          columnNames = {"publication_id"})
    },
    indexes = {
      @Index(name = "idx_indexing_status", columnList = "indexing_status"),
      @Index(name = "idx_data_source", columnList = "data_source"),
      @Index(name = "idx_import_batch", columnList = "import_batch"),
      @Index(name = "idx_review_status", columnList = "review_status")
    })
public class PublicationMetadataEntity extends ValueObjectJpaEntity {

  /// 文献 ID（外键：cat_publication.id，一对一关系）
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 索引状态：Pending/Indexed/MEDLINE/PubMed-not-MEDLINE 等
  @Column(name = "indexing_status", length = 50)
  private String indexingStatus;

  /// 索引方法：Automated/Curated/In-Data-Review
  @Column(name = "indexing_method", length = 50)
  private String indexingMethod;

  /// 索引日期
  @Column(name = "indexed_date")
  private LocalDate indexedDate;

  /// 数据来源：PubMed/EPMC/Crossref/Manual
  @Column(name = "data_source", length = 50)
  private String dataSource;

  /// 导入批次标识
  @Column(name = "import_batch", length = 50)
  private String importBatch;

  /// 导入时间
  @Column(name = "import_date")
  private Instant importDate;

  /// 数据记录所有者（NLM, NASA, PIP 等）
  @Column(name = "owner", length = 50)
  private String owner;

  /// 引用子集标识（IM, AIM 等）
  @Column(name = "citation_subset", length = 20)
  private String citationSubset;

  /// 质量评分：A/B/C/D/F
  @Column(name = "quality_score", length = 2)
  private String qualityScore;

  /// 完整性评分：A/B/C/D/F
  @Column(name = "completeness_score", length = 2)
  private String completenessScore;

  /// 是否有全文
  @Column(name = "has_full_text", nullable = false)
  private Boolean hasFullText = false;

  /// 全文链接
  @Column(name = "full_text_url", length = 200)
  private String fullTextUrl;

  /// 审核状态：Pending/Reviewed/Rejected/Approved
  @Column(name = "review_status", length = 50)
  private String reviewStatus;

  /// 审核日期
  @Column(name = "review_date")
  private LocalDate reviewDate;

  /// 审核人姓名
  @Column(name = "reviewer", length = 100)
  private String reviewer;

  /// 验证错误（JSON 数组）
  @Column(name = "validation_errors", columnDefinition = "JSON")
  private String validationErrors;

  /// 处理注释（JSON 数组）
  @Column(name = "processing_notes", columnDefinition = "JSON")
  private String processingNotes;
}
