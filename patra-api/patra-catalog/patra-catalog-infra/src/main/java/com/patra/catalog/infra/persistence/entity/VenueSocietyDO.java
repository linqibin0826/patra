package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 关联学会数据库实体（CQRS 补充数据），映射到表 `cat_venue_society`。
///
/// **设计说明**：
///
/// 与 `cat_venue` 表为 1:N 关系，一个载体可以关联多个学术组织。
/// 存储载体与学术组织（学会、协会等）的关联关系。
///
/// **数据来源**：OpenAlex
///
/// **使用场景**：
///
/// - 展示期刊关联的学术组织
/// - 按学术组织筛选期刊
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_society", autoResultMap = true)
public class VenueSocietyDO extends BaseDO {

  // ========================================
  // 关联信息
  // ========================================

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  // ========================================
  // 学会信息
  // ========================================

  /// 学会/组织 URL
  @TableField("url")
  private String url;

  /// 学会/组织名称
  @TableField("organization")
  private String organization;
}
