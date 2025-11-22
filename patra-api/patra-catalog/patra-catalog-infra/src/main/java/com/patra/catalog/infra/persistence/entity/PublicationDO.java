package com.patra.catalog.infra.persistence.entity;

import java.time.Instant;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 文献出版物数据库实体,映射到表 `cat_publication`。
/// 
/// 表结构: 文献出版物核心实体,包含标识符、标题、语言、出版信息、OA 状态、作者和引用信息。
/// 
/// 关键字段说明:
/// 
/// - `pmid` PubMed ID,唯一约束 uk_pmid
///   - `doi` 数字对象标识符,唯一约束 uk_doi
///   - `venue_id` 和 `venue_instance_id` 关联载体信息(冗余优化)
///   - `language_base` 生成列,基于 `language_code` 自动计算
///   - `is_oa` 和 `oa_status` OA 开放获取信息(冗余)
///   - `ext_data` JSON 扩展数据字段
/// 
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_publication", autoResultMap = true)
public class PublicationDO extends BaseDO {

  /// 数据来源代码
  @TableField("provenance_code")
  private String provenanceCode;

  /// PubMed ID
  @TableField("pmid")
  private String pmid;

  /// 数字对象标识符 DOI
  @TableField("doi")
  private String doi;

  /// 载体 ID
  @TableField("venue_id")
  private Long venueId;

  /// 载体实例 ID
  @TableField("venue_instance_id")
  private Long venueInstanceId;

  /// 文献标题
  @TableField("title")
  private String title;

  /// 原始语言标题
  @TableField("original_title")
  private String originalTitle;

  /// 原始语言值
  @TableField("language_raw")
  private String languageRaw;

  /// 标准语言代码
  @TableField("language_code")
  private String languageCode;

  /// 基础语种(生成列)。
/// 
/// 此字段由数据库自动维护,不应在应用层插入或更新。
  @TableField(
      value = "language_base",
      insertStrategy = FieldStrategy.NEVER,
      updateStrategy = FieldStrategy.NEVER)
  private String languageBase;

  /// 出版状态
  @TableField("publication_status")
  private String publicationStatus;

  /// 媒介类型
  @TableField("media_type")
  private String mediaType;

  /// 出版年份
  @TableField("publication_year")
  private Integer publicationYear;

  /// 是否有 OA 版本
  @TableField("is_oa")
  private Boolean isOa;

  /// 最佳 OA 状态
  @TableField("oa_status")
  private String oaStatus;

  /// 作者列表是否完整
  @TableField("authors_complete")
  private Boolean authorsComplete;

  /// 被引次数
  @TableField("citation_count")
  private Integer citationCount;

  /// 参考文献数量
  @TableField("number_of_references")
  private Integer numberOfReferences;

  /// 利益冲突声明
  @TableField("conflict_of_interest")
  private String conflictOfInterest;

  /// 扩展数据(JSON 格式)
  @TableField(value = "ext_data", typeHandler = JacksonTypeHandler.class)
  private JsonNode extData;

  /// 最后同步时间
  @TableField("last_synced_at")
  private Instant lastSyncedAt;

}
