package dev.patra.catalog.infra.publication.persistence.entity;

import dev.patra.tool.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
/// - 继承 `BaseJpaEntity` 获得审计与乐观锁；删除用 `deletedAt` 列标记
/// - 嵌入式值对象展开：`LanguageInfo` 直接映射为字段，`pmid`/`doi` 为扁平字段
/// - `language_base` 是数据库生成列，设为只读
/// - 枚举类型（ProvenanceCode、PublicationStatus 等）存储为 String
///
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "cat_publication")
public class PublicationEntity extends BaseJpaEntity {

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

  /// 载体实例 ID（引用 cat_venue_instance.id，不建外键）
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

  /// 出版月份（主出版日期冗余；只到年时为 null）
  @Column(name = "publication_month")
  private Integer publicationMonth;

  /// 出版日（主出版日期冗余；只到年月时为 null）
  @Column(name = "publication_day")
  private Integer publicationDay;

  /// PubMed MedlinePgn 原文（NLM 缩写形式，如 `1451-61`）
  @Column(name = "medline_pgn", length = 200)
  private String medlinePgn;

  /// PubMed StartPage
  @Column(name = "start_page", length = 50)
  private String startPage;

  /// PubMed EndPage
  @Column(name = "end_page", length = 50)
  private String endPage;

  /// 期刊 ISO 缩写（Journal/ISOAbbreviation）
  @Column(name = "journal_iso_abbreviation", length = 200)
  private String journalIsoAbbreviation;

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

  /// 删除时间，空表示未删除；查询方法自己带「未删除」条件（库表规范第 5 节）。
  @Column(name = "deleted_at")
  private Instant deletedAt;
}
