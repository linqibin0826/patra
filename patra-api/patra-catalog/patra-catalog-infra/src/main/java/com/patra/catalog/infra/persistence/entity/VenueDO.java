package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 出版载体数据库实体，映射到表 `cat_venue`。
///
/// 表结构：管理期刊、仓库、会议、电子书平台等出版载体的基本信息。
/// 支持多数据源导入（OpenAlex、PubMed Catalog、DOAJ、Crossref、JCR）。
///
/// 关键字段说明：
///
/// - `venue_type` 载体类型：JOURNAL/REPOSITORY/CONFERENCE/EBOOK_PLATFORM/BOOK_SERIES/METADATA/OTHER
/// - `openalex_id` / `issn_l` / `nlm_id` 冗余标识符（高频查询优化）
/// - `publication_start_year` / `publication_end_year` / `ceased` 出版历史（来自 PubMed）
/// - `indexing_status` / `medline_ta` / `iso_abbreviation` 索引收录信息（来自 PubMed）
/// - `latest_impact_score` / `latest_quartile` 最新评级快照（冗余，来自 JCR/CAS/Scopus）
/// - `alternate_titles` 替代名称列表（JSON 数组）
/// - `apc_prices` APC 费用列表（JSON 数组，含不同货币价格）
/// - `societies` 关联学术组织（JSON 数组）
/// - `host_organization_lineage` 机构所有权链（JSON 数组）
/// - `ext_data` 扩展数据（h_index、i10_index 等来源特定字段）
///
/// 索引说明：
///
/// - 唯一索引 `uk_openalex_id`: OpenAlex ID 唯一
/// - 唯一索引 `uk_issn_l`: ISSN-L 唯一
/// - 唯一索引 `uk_nlm_id`: NLM ID 唯一
/// - 普通索引 `idx_venue_type`: 载体类型
/// - 普通索引 `idx_indexing_status`: MEDLINE 收录状态
/// - 普通索引 `idx_latest_quartile`: 最新分区
/// - 全文索引 `ft_display_name`: 名称全文检索
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue", autoResultMap = true)
public class VenueDO extends BaseDO {

  // ========================================
  // 基本信息
  // ========================================

  /// 载体类型：JOURNAL/REPOSITORY/CONFERENCE/EBOOK_PLATFORM/BOOK_SERIES/METADATA/OTHER
  @TableField("venue_type")
  private String venueType;

  /// 载体显示名称（主名称）
  @TableField("display_name")
  private String displayName;

  /// 缩写标题（来自 ISSN 中心或 ISO）
  @TableField("abbreviated_title")
  private String abbreviatedTitle;

  /// 替代名称列表（JSON 数组，包含缩写和翻译名）
  @TableField(value = "alternate_titles", typeHandler = JacksonTypeHandler.class)
  private List<String> alternateTitles;

  /// 载体主页 URL
  @TableField("homepage_url")
  private String homepageUrl;

  // ========================================
  // 冗余标识符（高频查询优化）
  // ========================================

  /// OpenAlex ID（冗余，格式：S1234567890）
  @TableField("openalex_id")
  private String openalexId;

  /// Linking ISSN（冗余，关联纸质版和电子版）
  @TableField("issn_l")
  private String issnL;

  /// NLM 唯一标识符（冗余，来自 PubMed Catalog）
  @TableField("nlm_id")
  private String nlmId;

  /// DOI 前缀（来自 Crossref）
  @TableField("doi_prefix")
  private String doiPrefix;

  // ========================================
  // 出版商信息
  // ========================================

  /// 出版商名称（来自 Crossref/DOAJ）
  @TableField("publisher")
  private String publisher;

  // ========================================
  // 宿主机构信息
  // ========================================

  /// 宿主机构 OpenAlex ID
  @TableField("host_organization_id")
  private String hostOrganizationId;

  /// 宿主机构名称（出版商/机构）
  @TableField("host_organization_name")
  private String hostOrganizationName;

  /// 机构所有权链（JSON 数组，从直接母公司到顶层）
  @TableField(value = "host_organization_lineage", typeHandler = JacksonTypeHandler.class)
  private List<String> hostOrganizationLineage;

  // ========================================
  // 地理信息
  // ========================================

  /// 国家代码（ISO 3166-1 alpha-2，如 US/CN）
  @TableField("country_code")
  private String countryCode;

  // ========================================
  // 出版历史（来自 PubMed Catalog）
  // ========================================

  /// 创刊年份
  @TableField("publication_start_year")
  private Short publicationStartYear;

  /// 停刊年份（期刊已停刊时设置）
  @TableField("publication_end_year")
  private Short publicationEndYear;

  /// 是否已停刊
  @TableField("ceased")
  private Boolean ceased;

  // ========================================
  // 索引收录信息（来自 PubMed Catalog）
  // ========================================

  /// MEDLINE 收录状态：MEDLINE/PUBMED/IN_PROCESS/NOT_INDEXED
  @TableField("indexing_status")
  private String indexingStatus;

  /// MEDLINE 缩写标题
  @TableField("medline_ta")
  private String medlineTa;

  /// ISO 缩写标题
  @TableField("iso_abbreviation")
  private String isoAbbreviation;

  // ========================================
  // OA 状态
  // ========================================

  /// 是否为开放获取来源
  @TableField("is_oa")
  private Boolean isOa;

  /// 是否收录于 DOAJ
  @TableField("is_in_doaj")
  private Boolean isInDoaj;

  /// OA 类型：GOLD/DIAMOND/HYBRID/BRONZE（来自 DOAJ）
  @TableField("oa_type")
  private String oaType;

  // ========================================
  // 统计指标（当前快照）
  // ========================================

  /// 托管作品数量
  @TableField("works_count")
  private Integer worksCount;

  /// 被引用次数总计
  @TableField("cited_by_count")
  private Integer citedByCount;

  // ========================================
  // 最新评级快照（冗余，高频查询优化）
  // ========================================

  /// 最新影响力分数（JIF/CiteScore 等）
  @TableField("latest_impact_score")
  private BigDecimal latestImpactScore;

  /// 最新分区（Q1-Q4 或 1区-4区）
  @TableField("latest_quartile")
  private String latestQuartile;

  /// 最新评级来源：JCR/CAS/SCOPUS
  @TableField("latest_rating_system")
  private String latestRatingSystem;

  /// 最新评级年份
  @TableField("latest_rating_year")
  private Short latestRatingYear;

  // ========================================
  // APC 信息（文章处理费）
  // ========================================

  /// 文章处理费（美元）
  @TableField("apc_usd")
  private Integer apcUsd;

  /// APC 费用列表（JSON 数组，含不同货币价格）
  ///
  /// 格式示例：`[{"price": 3000, "currency": "USD"}, {"price": 2500, "currency": "EUR"}]`
  @TableField(value = "apc_prices", typeHandler = JacksonTypeHandler.class)
  private JsonNode apcPrices;

  // ========================================
  // 关联学会
  // ========================================

  /// 关联学术组织（JSON 数组，含 url 和 organization）
  ///
  /// 格式示例：`[{"url": "https://www.acs.org/", "organization": "American Chemical Society"}]`
  @TableField(value = "societies", typeHandler = JacksonTypeHandler.class)
  private JsonNode societies;

  // ========================================
  // 数据来源与同步
  // ========================================

  /// 数据来源代码：OPENALEX/PUBMED/CROSSREF/MANUAL
  @TableField("provenance_code")
  private String provenanceCode;

  /// 来源系统创建日期
  @TableField("source_created_date")
  private LocalDate sourceCreatedDate;

  /// 来源系统更新日期
  @TableField("source_updated_date")
  private LocalDate sourceUpdatedDate;

  /// 最后同步时间（UTC）
  @TableField("last_synced_at")
  private Instant lastSyncedAt;

  // ========================================
  // 扩展数据
  // ========================================

  /// 扩展数据（灵活存储来源特定字段）
  @TableField(value = "ext_data", typeHandler = JacksonTypeHandler.class)
  private JsonNode extData;
}
