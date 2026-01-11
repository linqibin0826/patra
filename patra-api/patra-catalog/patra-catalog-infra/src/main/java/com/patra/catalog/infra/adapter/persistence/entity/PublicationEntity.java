package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.SoftDeletableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 文献出版物 JPA 实体，映射到表 `cat_publication`。
///
/// **设计说明**：
///
/// - 继承 `BaseJpaEntity` 获得审计、乐观锁、软删除功能
/// - 嵌入式值对象展开：`PublicationIdentifiers` 和 `LanguageInfo` 直接映射为字段
/// - `language_base` 是数据库生成列，设为只读
/// - 枚举类型（ProvenanceCode、PublicationStatus 等）存储为 String
///
/// **索引设计**：
///
/// - `uk_pmid`：PMID 唯一索引
/// - `uk_doi`：DOI 唯一索引
/// - `idx_venue_id`：载体 ID 索引
/// - `idx_venue_instance_id`：载体实例 ID 索引
/// - `idx_publication_year`：出版年份索引
/// - `idx_language_base`：基础语种索引（用于按语系查询）
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
    name = "cat_publication",
    indexes = {
      @Index(name = "uk_pmid", columnList = "pmid", unique = true),
      @Index(name = "uk_doi", columnList = "doi", unique = true),
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_venue_instance_id", columnList = "venue_instance_id"),
      @Index(name = "idx_publication_year", columnList = "publication_year"),
      @Index(name = "idx_language_base", columnList = "language_base")
    })
public class PublicationEntity extends SoftDeletableJpaEntity {

  // ========== 数据来源追踪 ==========

  /// 数据来源代码（PUBMED, EPMC, CROSSREF 等）
  @Column(name = "provenance_code", nullable = false, length = 50)
  private String provenanceCode;

  /// 最后同步时间（用于增量更新判断）
  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  // ========== 标识符（嵌入式值对象展开） ==========

  /// PubMed ID（1-15位数字，可为 null）
  @Column(name = "pmid", length = 15)
  private String pmid;

  /// Digital Object Identifier（最长200字符，可为 null）
  @Column(name = "doi", length = 200)
  private String doi;

  // ========== 关联关系 ==========

  /// 载体 ID（冗余优化：避免二级 JOIN）
  @Column(name = "venue_id")
  private Long venueId;

  /// 载体实例 ID（外键：cat_venue_instance.id）
  @Column(name = "venue_instance_id", nullable = false)
  private Long venueInstanceId;

  // ========== 标题 ==========

  /// 文献标题（英文或原语言）
  @Column(name = "title", nullable = false, columnDefinition = "TEXT")
  private String title;

  /// 原始语言标题（非英文时填充）
  @Column(name = "original_title", columnDefinition = "TEXT")
  private String originalTitle;

  // ========== 语言信息（嵌入式值对象展开） ==========

  /// 原始语言值（外部采集）
  @Column(name = "language_raw", length = 100)
  private String languageRaw;

  /// 标准语言代码（BCP 47，如 zh-CN, en-US）
  @Column(name = "language_code", length = 20)
  private String languageCode;

  /// 基础语种代码（数据库生成列，只读）。
  ///
  /// 此字段由数据库 `GENERATED ALWAYS AS` 自动计算，应用层不应写入。
  @Column(name = "language_base", length = 10, insertable = false, updatable = false)
  private String languageBase;

  // ========== 出版信息 ==========

  /// 出版状态（PUBLISHED, IN_PRESS, PREPRINT 等）
  @Column(name = "publication_status", length = 50)
  private String publicationStatus;

  /// 媒介类型（PRINT, ELECTRONIC, BOTH 等）
  @Column(name = "media_type", length = 50)
  private String mediaType;

  /// 出版年份（1800-2100）
  @Column(name = "publication_year", nullable = false)
  private Integer publicationYear;

  // ========== OA 信息 ==========

  /// 是否有 OA 版本（冗余：快速筛选）
  @Column(name = "is_oa")
  private Boolean isOa;

  /// 最佳 OA 状态（gold/green/hybrid/bronze/closed）
  @Column(name = "oa_status", length = 20)
  private String oaStatus;

  // ========== 统计信息 ==========

  /// 作者列表是否完整
  @Column(name = "authors_complete")
  private Boolean authorsComplete;

  /// 被引次数（定期更新）
  @Column(name = "citation_count")
  private Integer citationCount;

  /// 参考文献数量
  @Column(name = "number_of_references")
  private Integer numberOfReferences;

  // ========== 其他信息 ==========

  /// 利益冲突声明
  @Column(name = "conflict_of_interest", columnDefinition = "TEXT")
  private String conflictOfInterest;

  /// 扩展数据（JSON 格式，灵活扩展）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "ext_data", columnDefinition = "JSON")
  private JsonNode extData;
}
