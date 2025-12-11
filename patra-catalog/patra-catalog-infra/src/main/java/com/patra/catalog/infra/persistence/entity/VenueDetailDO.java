package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 载体详情数据库实体（CQRS 补充数据），映射到表 `cat_venue_detail`。
///
/// **设计说明**：
///
/// 与 `cat_venue` 表为 1:1 关系，通过 `venue_id` 唯一索引保证。
/// 合并了出版信息、索引信息、OA 状态、宿主机构等非核心属性。
///
/// **数据来源**：
///
/// - OpenAlex：出版信息、宿主机构、OA 状态
/// - PubMed Serfile：语言信息、索引信息
///
/// **字段分组**：
///
/// | 分组 | 字段 |
/// |------|------|
/// | 出版信息 | alternate_titles, homepage_url, frequency |
/// | 出版历史 | publication_start_year, publication_end_year, ceased |
/// | 语言信息 | languages |
/// | 宿主机构 | host_organization_id, host_organization_name, host_organization_lineage |
/// | 索引信息 | indexing_status, medline_ta, iso_abbreviation |
/// | OA 状态 | is_oa, is_in_doaj, oa_type |
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_detail", autoResultMap = true)
public class VenueDetailDO extends BaseDO {

  // ========================================
  // 关联信息
  // ========================================

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  // ========================================
  // 出版信息（来自 OpenAlex）
  // ========================================

  /// 替代名称列表（JSON 数组）
  @TableField(value = "alternate_titles", typeHandler = JacksonTypeHandler.class)
  private List<String> alternateTitles;

  /// 载体主页 URL
  @TableField("homepage_url")
  private String homepageUrl;

  /// 出版频率（Weekly/Monthly/Quarterly 等）
  @TableField("frequency")
  private String frequency;

  // ========================================
  // 出版历史（来自 OpenAlex/PubMed）
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
  // 语言信息（来自 Serfile）
  // ========================================

  /// 期刊语言信息（JSON 对象，含 primary 和 summary 数组）
  ///
  /// 格式示例：`{"primary": ["eng"], "summary": ["fre", "ger"]}`
  @TableField(value = "languages", typeHandler = JacksonTypeHandler.class)
  private JsonNode languages;

  // ========================================
  // 宿主机构（来自 OpenAlex）
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
  // 索引信息（来自 PubMed Catalog）
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
  // OA 状态（来自 OpenAlex）
  // ========================================

  /// 是否为开放获取来源
  @TableField("is_oa")
  private Boolean isOa;

  /// 是否收录于 DOAJ
  @TableField("is_in_doaj")
  private Boolean isInDoaj;

  /// OA 类型：GOLD/DIAMOND/HYBRID/BRONZE
  @TableField("oa_type")
  private String oaType;

  // ========================================
  // 扩展数据
  // ========================================

  /// 扩展数据（灵活存储来源特定字段）
  @TableField(value = "ext_data", typeHandler = JacksonTypeHandler.class)
  private Map<String, Object> extData;
}
