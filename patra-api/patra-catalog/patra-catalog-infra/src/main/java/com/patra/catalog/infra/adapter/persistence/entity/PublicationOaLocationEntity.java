package com.patra.catalog.infra.adapter.persistence.entity;

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

/// 文献开放获取位置 JPA 实体，映射到表 `cat_publication_oa_location`。
///
/// **表结构**：详细记录文献的开放获取位置，支持多位置管理和最佳位置选择。
///
/// **关键字段说明**：
///
/// - `publication_id` 文献 ID，外键关联 cat_publication.id
/// - `oa_status` OA 状态：gold/green/hybrid/bronze/closed
/// - `location_type` 位置类型：publisher/repository/pubmed_central/preprint
/// - `is_best` 是否最佳位置
///
/// **索引说明**：
///
/// - 复合唯一索引 `uk_oa_url`: (publication_id, url) 防止重复记录
/// - 普通索引支持按文献、OA 状态、位置类型查询
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_publication_oa_location",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_oa_url",
          columnNames = {"publication_id", "url"})
    },
    indexes = {
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_oa_status", columnList = "oa_status"),
      @Index(name = "idx_location_type", columnList = "location_type")
    })
public class PublicationOaLocationEntity extends ValueObjectJpaEntity {

  /// 文献 ID（外键：cat_publication.id）
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// OA 状态：gold/green/hybrid/bronze/closed
  @Column(name = "oa_status", nullable = false, length = 20)
  private String oaStatus;

  /// 位置类型：publisher/repository/pubmed_central/preprint
  @Column(name = "location_type", length = 50)
  private String locationType;

  /// 访问 URL
  @Column(name = "url", length = 500)
  private String url;

  /// 托管域名
  @Column(name = "host_domain", length = 200)
  private String hostDomain;

  /// 仓库名称
  @Column(name = "repository_name", length = 100)
  private String repositoryName;

  /// 仓库标识符
  @Column(name = "repository_id", length = 100)
  private String repositoryId;

  /// 版本类型：publishedVersion/acceptedVersion/submittedVersion
  @Column(name = "version_type", length = 50)
  private String versionType;

  /// 许可证
  @Column(name = "license", length = 100)
  private String license;

  /// 可用日期
  @Column(name = "available_date")
  private LocalDate availableDate;

  /// 禁发期结束日期
  @Column(name = "embargo_end_date")
  private LocalDate embargoEndDate;

  /// 是否最佳位置
  @Column(name = "is_best", nullable = false)
  private Boolean isBest = false;

  /// 优先级（数值越小优先级越高）
  @Column(name = "priority")
  private Integer priority;

  /// 证据来源（Unpaywall/OpenAlex）
  @Column(name = "evidence_source", length = 50)
  private String evidenceSource;

  /// 链接检查时间
  @Column(name = "checked_date")
  private Instant checkedDate;

  /// 是否有效
  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  /// PMC ID
  @Column(name = "pmcid", length = 200)
  private String pmcid;
}
