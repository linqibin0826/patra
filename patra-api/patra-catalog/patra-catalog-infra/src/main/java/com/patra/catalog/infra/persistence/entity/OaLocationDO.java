package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/// 开放获取位置数据库实体,映射到表 `cat_oa_location`。
/// 
/// 表结构: 详细记录文献的开放获取位置,支持多位置管理和最佳位置选择
/// 
/// 关键字段说明:
/// 
/// - `publication_id` 出版物ID(外键:cat_publication.id)
///   - `oa_status` OA状态(gold/green/hybrid/bronze/closed)
///   - `url` 访问URL,唯一约束 uk_oa_url (publication_id + url)
///   - `is_best` 是否最佳位置
///   - `priority` 优先级(1=最高,数值越小优先级越高)
///   - `available_date` 可用日期
///   - `access_metrics` 访问指标(下载次数等)
///   - `metadata` 位置元数据(灵活扩展)
/// 
/// @author linqibin
/// @since 0.5.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_oa_location", autoResultMap = true)
public class OaLocationDO extends BaseDO {
  /// 出版物ID(外键:cat_publication.id)
  @TableField("publication_id")
  private Long publicationId;

  /// OA状态:gold/green/hybrid/bronze/closed
  @TableField("oa_status")
  private String oaStatus;

  /// 位置类型:publisher/repository/pubmed_central/preprint/academic_social/other
  @TableField("location_type")
  private String locationType;

  /// 访问URL
  @TableField("url")
  private String url;

  /// 托管域名(如"pubmed.ncbi.nlm.nih.gov")
  @TableField("host_domain")
  private String hostDomain;

  /// 仓库名称(如"PubMed Central","arXiv")
  @TableField("repository_name")
  private String repositoryName;

  /// 仓库标识符(如"PMC1234567")
  @TableField("repository_id")
  private String repositoryId;
  /// 许可证(如"CC-BY-4.0","CC-BY-NC")
  @TableField("license")
  private String license;

  /// 可用日期
  @TableField("available_date")
  private Instant availableDate;

  /// 禁发期结束日期
  @TableField("embargo_end_date")
  private Instant embargoEndDate;

  /// 是否最佳位置(0=否,1=是)
  @TableField("is_best")
  private Boolean isBest;

  /// 优先级(1=最高,数值越小优先级越高)
  @TableField("priority")
  private Integer priority;

  /// 证据来源(如"Unpaywall","OpenAlex")
  @TableField("evidence_source")
  private String evidenceSource;

  /// 检查日期(最后验证链接有效性的日期)
  @TableField("checked_date")
  private Instant checkedDate;

  /// 是否有效(0=失效,1=有效)
  @TableField("is_active")
  private Boolean isActive;

  /// PMC ID(如"PMC1234567",PMC专用)
  @TableField("pmcid")
  private String pmcid;

  /// 访问指标(下载次数等)
  @TableField(value = "access_metrics", typeHandler = JacksonTypeHandler.class)
  private JsonNode accessMetrics;

  /// 位置元数据(灵活扩展)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;

}
