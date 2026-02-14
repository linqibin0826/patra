package com.patra.catalog.infra.persistence.entity;

import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.starter.jpa.entity.SoftDeletableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// 出版载体 JPA 实体，映射到表 `cat_venue`。
///
/// **DDD 嵌入式值对象设计**：
///
/// 遵循 DDD 原则，聚合根包含核心身份标识、来源追踪和嵌入式值对象（JSON 字段）。
///
/// **嵌入式值对象**（JSON 字段）：
///
/// - `publication_profile` - 出版概况（出版历史、语言、宿主机构、索引信息等）
/// - `citation_metrics` - 引用指标（works_count、cited_by_count、h_index 等）
/// - `open_access` - 开放获取信息（OA 状态 + APC 定价）
/// - `affiliated_societies` - 关联学会列表
///
/// **快速访问字段**（优化查询性能）：
///
/// | 字段 | 来源 | 策略 |
/// |------|------|------|
/// | nlm_id | cat_venue_identifier | 冗余 |
/// | issn_l | cat_venue_identifier | 冗余 |
/// | openalex_id | cat_venue_identifier | 冗余 |
/// | abbreviated_title | PublicationProfile | 快照 |
/// | primary_language | PublicationProfile | 快照 |
/// | country_code | PublicationProfile | 快照 |
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_venue",
    indexes = {
      @Index(name = "idx_venue_type", columnList = "venue_type"),
      @Index(name = "idx_display_name", columnList = "display_name"),
      @Index(name = "idx_provenance", columnList = "provenance_code"),
      @Index(name = "idx_issn_l", columnList = "issn_l"),
      @Index(name = "idx_nlm_id", columnList = "nlm_id"),
      @Index(name = "idx_openalex_id", columnList = "openalex_id")
    })
public class VenueEntity extends SoftDeletableJpaEntity {

  // ========================================
  // 核心属性（不变量验证所需）
  // ========================================

  /// 载体类型：JOURNAL/REPOSITORY/CONFERENCE/EBOOK_PLATFORM/BOOK_SERIES/METADATA/OTHER
  @Column(name = "venue_type", nullable = false, length = 20)
  private String venueType;

  /// 载体显示名称（主名称）
  @Column(name = "display_name", nullable = false, length = 500)
  private String displayName;

  // ========================================
  // 来源追踪（Provenance）
  // ========================================

  /// 数据来源代码：OPENALEX/PUBMED/CROSSREF/DOAJ/MANUAL
  @Column(name = "provenance_code", nullable = false, length = 20)
  private String provenanceCode;

  // ========================================
  // 快速访问字段
  // ========================================

  /// NLM 唯一标识符。
  @Column(name = "nlm_id", length = 20)
  private String nlmId;

  /// Linking ISSN。
  @Column(name = "issn_l", length = 10)
  private String issnL;

  /// OpenAlex Source ID。
  @Column(name = "openalex_id", length = 50)
  private String openalexId;

  /// 缩写标题。
  @Column(name = "abbreviated_title", length = 255)
  private String abbreviatedTitle;

  /// 主要语言代码（ISO 639-3）。
  @Column(name = "primary_language", length = 10)
  private String primaryLanguage;

  /// 国家代码（ISO 3166-1 alpha-2）。
  @Column(name = "country_code", length = 2)
  private String countryCode;

  /// 最后同步时间（UTC）
  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  // ========================================
  // 嵌入式值对象（JSON 字段）
  // ========================================

  /// 出版概况（出版历史、语言、宿主机构、索引信息等）。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "publication_profile", columnDefinition = "JSON")
  private PublicationProfile publicationProfile;

  /// 引用指标（works_count、cited_by_count、h_index 等）。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "citation_metrics", columnDefinition = "JSON")
  private CitationMetrics citationMetrics;

  /// 开放获取信息（OA 状态 + APC 定价）。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "open_access", columnDefinition = "JSON")
  private OpenAccessInfo openAccess;

  /// 关联学会列表。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "affiliated_societies", columnDefinition = "JSON")
  private List<Society> affiliatedSocieties;
}
