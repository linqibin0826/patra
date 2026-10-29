package dev.patra.catalog.infra.fulltext.persistence.entity;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// 全文记录实体，映射 `cat_publication_fulltext`。
///
/// 状态与原因存枚举名；`media` 以 JSONB 直接映射领域 record 列表。审计列由数据库维护，实体不映射。
///
/// @author linqibin
/// @since 0.11.0
@Getter
@Setter
@Entity
@Table(name = "cat_publication_fulltext")
public class PublicationFullTextEntity extends ValueObjectJpaEntity {

  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  @Column(name = "pmcid", nullable = false, length = 20)
  private String pmcid;

  @Column(name = "pmc_version", length = 30)
  private String pmcVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private FullTextStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "unavailable_reason", length = 30)
  private FullTextUnavailableReason unavailableReason;

  @Column(name = "license_code", length = 30)
  private String licenseCode;

  @Column(name = "is_manuscript")
  private Boolean manuscript;

  @Column(name = "object_prefix", length = 200)
  private String objectPrefix;

  @Column(name = "xml_md5", length = 32)
  private String xmlMd5;

  @Column(name = "pdf_object_key", length = 300)
  private String pdfObjectKey;

  @Column(name = "pdf_size")
  private Long pdfSize;

  @Column(name = "pdf_md5", length = 32)
  private String pdfMd5;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "media", nullable = false, columnDefinition = "jsonb")
  private List<FullTextMedia> media = new ArrayList<>();

  @Column(name = "fetched_at")
  private Instant fetchedAt;

  @Column(name = "parser_version")
  private Integer parserVersion;

  @Column(name = "parsed_at")
  private Instant parsedAt;

  @Column(name = "word_count")
  private Integer wordCount;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error", length = 2000)
  private String lastError;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;
}
