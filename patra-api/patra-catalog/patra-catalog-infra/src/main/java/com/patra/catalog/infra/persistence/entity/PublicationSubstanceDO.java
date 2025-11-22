package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 文献-物质关联数据库实体,映射到表 `cat_publication_substance`。
///
/// 表结构: 存储文献涉及的化学物质标注,关联文献和物质,支持主/副物质标记和角色。
///
/// 关键字段说明:
///
/// - `publication_id` 出版物 ID(外键:cat_publication.id)
///   - `substance_id` 物质 ID(外键:cat_substance.id)
///   - `is_major` 是否主要物质(0=副物质,1=主要物质)
///   - `role` 物质角色(枚举:therapeutic/diagnostic/research_tool/adverse_effect/target/metabolite)
///
/// 索引说明:
///
/// - `idx_pub_substance` 文献+物质复合索引,支持查询文献的物质(<20ms)
///   - `idx_substance_pub` 物质+文献复合索引,支持查询物质的文献(<50ms)
///   - `idx_major_role` 物质+主/副标记+角色复合索引,支持多条件筛选
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cat_publication_substance")
public class PublicationSubstanceDO extends BaseDO {
  /// 出版物 ID(外键:cat_publication.id)
  @TableField("publication_id")
  private Long publicationId;

  /// 物质 ID(外键:cat_substance.id)
  @TableField("substance_id")
  private Long substanceId;

  /// 是否主要物质(0=副物质,1=主要物质)
  @TableField("is_major")
  private Boolean isMajor;

  /// 物质角色(枚举:therapeutic/diagnostic/research_tool/adverse_effect/target/metabolite)
  @TableField("role")
  private String role;
}
