package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 载体关联关系数据库实体，映射到表 `cat_venue_relation`。
///
/// 表结构：存储期刊之间的历史关系（前身、后继、合并、拆分等），来源于 NLM Serfile。
///
/// 关键字段说明：
///
/// - `venue_id` 载体 ID，外键关联 cat_venue.id
/// - `related_venue_id` 关联期刊 ID（如果已在系统中）
/// - `related_nlm_id` 关联期刊的 NLM ID
/// - `related_title` 关联期刊标题（必填）
/// - `relation_type` 关系类型枚举：PRECEDING/SUCCEEDING/ABSORBED/MERGED 等
/// - `effective_date` 关系生效日期
///
/// 索引说明：
///
/// - 唯一索引 `uk_venue_relation`: (venue_id, related_title, relation_type) 防止重复
/// - 普通索引 `idx_venue_id`: venue_id 支持查询期刊的所有关联关系
/// - 普通索引 `idx_related_venue_id`: related_venue_id 支持反向查询
/// - 普通索引 `idx_relation_type`: relation_type 支持按关系类型筛选
///
/// 使用场景：
///
/// 1. 从 Serfile 导入期刊的 TitleRelated 数据
/// 2. 追溯期刊的历史演变（合并、拆分、更名）
/// 3. 关联引用数据统计（合并前后期刊的被引用统计）
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_relation")
public class VenueRelationDO extends BaseDO {

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  /// 关联期刊 ID（如果已在系统中）
  @TableField("related_venue_id")
  private Long relatedVenueId;

  /// 关联期刊 NLM ID
  @TableField("related_nlm_id")
  private String relatedNlmId;

  /// 关联期刊标题
  @TableField("related_title")
  private String relatedTitle;

  /// 关系类型：PRECEDING/SUCCEEDING/ABSORBED/ABSORBED_BY/MERGED/SPLIT_FROM 等
  @TableField("relation_type")
  private String relationType;

  /// 生效日期
  @TableField("effective_date")
  private LocalDate effectiveDate;

  /// 备注说明
  @TableField("notes")
  private String notes;
}
