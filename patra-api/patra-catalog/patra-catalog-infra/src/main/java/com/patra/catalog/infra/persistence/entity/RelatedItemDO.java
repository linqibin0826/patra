package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 相关项目数据库实体,映射到表 `cat_related_item`。
///
/// 表结构: 管理文献的相关项(撤稿、勘误、评论等),支持 12 种关联类型。
///
/// 关键字段说明:
///
/// - `publication_id` 主文献 ID(外键:cat_publication.id)
///   - `related_publication_id` 相关文献 ID(如果在库中)(外键:cat_publication.id)
///   - `related_pmid` 相关文献 PMID(库外)
///   - `related_doi` 相关文献 DOI(库外)
///   - `relationship_type` 关系类型,CHECK 约束 12 个枚举值
///   - `relationship_date` 关系建立日期(DATE),映射为 Instant（UTC午夜）
///   - `initiated_by` 发起方,CHECK 约束 5 个枚举值
///   - `status` 状态,CHECK 约束 4 个枚举值
///   - `metadata` JSON 扩展数据字段
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_related_item", autoResultMap = true)
public class RelatedItemDO extends BaseDO {
  /// 主文献 ID(外键:cat_publication.id)
  @TableField("publication_id")
  private Long publicationId;

  /// 相关文献 ID(如果在库中)(外键:cat_publication.id)
  @TableField("related_publication_id")
  private Long relatedPublicationId;

  /// 相关文献 PMID(库外)
  @TableField("related_pmid")
  private String relatedPmid;

  /// 相关文献 DOI(库外)
  @TableField("related_doi")
  private String relatedDoi;

  /// 关系类型。
  ///
  /// CHECK 约束确保值在枚举范围内: Retraction/Partial Retraction/Expression of Concern/Withdrawn/
  /// Erratum/Correction/Comment/Response/Update/Republication/Superseded/Duplicate。
  @TableField("relationship_type")
  private String relationshipType;

  /// 相关项标题
  @TableField("title")
  private String title;

  /// 关系描述
  @TableField("description")
  private String description;

  /// 关系建立日期
  @TableField("relationship_date")
  private Instant relationshipDate;

  /// 发起方。
  ///
  /// CHECK 约束确保值在枚举范围内: Author/Editor/Publisher/Institution/Third Party。
  @TableField("initiated_by")
  private String initiatedBy;

  /// 状态。
  ///
  /// CHECK 约束确保值在枚举范围内: Active/Resolved/Under Investigation/Pending。
  @TableField("status")
  private String status;

  /// 顺序号(用于排序显示)
  @TableField("order_num")
  private Integer orderNum;

  /// 关系元数据(JSON 格式)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
