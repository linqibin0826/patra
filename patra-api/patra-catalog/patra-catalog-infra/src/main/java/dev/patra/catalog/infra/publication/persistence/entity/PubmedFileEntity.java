package dev.patra.catalog.infra.publication.persistence.entity;

import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedFileStatus;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/// PubMed 文件（baseline 与 updatefiles）处理进度实体，映射 `cat_pubmed_file`。
///
/// 审计列由数据库默认值与 `set_updated_at()` 触发器维护，实体不映射。
///
/// @author linqibin
/// @since 0.11.0
@Getter
@Setter
@Entity
@Table(name = "cat_pubmed_file")
public class PubmedFileEntity extends ValueObjectJpaEntity {

  @Column(name = "file_name", nullable = false, length = 40)
  private String fileName;

  @Column(name = "file_seq", nullable = false)
  private int fileSeq;

  /// 文件种类（baseline / updatefiles）
  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 10)
  private PubmedFileKind kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PubmedFileStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false, length = 20)
  private PubmedImportScope scope;

  @Column(name = "created_count", nullable = false)
  private int createdCount;

  @Column(name = "updated_count", nullable = false)
  private int updatedCount;

  @Column(name = "deleted_count", nullable = false)
  private int deletedCount;

  @Column(name = "skipped_absent_count", nullable = false)
  private int skippedAbsentCount;

  @Column(name = "skipped_invalid_count", nullable = false)
  private int skippedInvalidCount;

  @Column(name = "doi_conflict_count", nullable = false)
  private int doiConflictCount;

  @Column(name = "skip_error_count", nullable = false)
  private int skipErrorCount;

  /// 比库里版本旧而跳过的记录数
  @Column(name = "skipped_stale_count", nullable = false)
  private int skippedStaleCount;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "last_error", length = 2000)
  private String lastError;

  @Column(name = "job_generation", length = 64)
  private String jobGeneration;
}
