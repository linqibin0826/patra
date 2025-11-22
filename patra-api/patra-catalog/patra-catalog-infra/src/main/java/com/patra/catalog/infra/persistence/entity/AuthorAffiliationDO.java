package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 作者-机构关联数据库实体,映射到表 `cat_author_affiliation`。
///
/// 表结构: 管理作者与机构的多对多关系,支持时间维度追踪和特定文献上下文。
///
/// 关键字段说明:
///
/// - `author_id` 作者ID,外键 cat_author.id
///   - `affiliation_id` 机构ID,外键 cat_affiliation.id
///   - `publication_id` 文献ID,外键 cat_publication.id（可选）
///   - `start_date` 开始日期,CHECK 约束 chk_date_range
///   - `end_date` 结束日期,CHECK 约束 chk_date_range
///   - `metadata` JSON 扩展数据字段
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_author_affiliation", autoResultMap = true)
public class AuthorAffiliationDO extends BaseDO {
  /// 作者ID（外键:cat_author.id）
  @TableField("author_id")
  private Long authorId;

  /// 机构ID（外键:cat_affiliation.id）
  @TableField("affiliation_id")
  private Long affiliationId;

  /// 文献ID（外键:cat_publication.id,可选）
  @TableField("publication_id")
  private Long publicationId;

  /// 开始日期（作者加入机构日期）
  @TableField("start_date")
  private Instant startDate;

  /// 结束日期（作者离开机构日期）
  @TableField("end_date")
  private Instant endDate;

  /// 关联类型（如"current","past","visiting"）
  @TableField("affiliation_type")
  private String affiliationType;

  /// 是否主要机构（false=否,true=是）
  @TableField("is_primary")
  private Boolean isPrimary;

  /// 机构顺序（作者有多个机构时排序）
  @TableField("order_num")
  private Integer orderNum;

  /// 关联元数据（JSON 格式）
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
