package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 出版载体数据库实体（最小聚合根），映射到表 `cat_venue`。
///
/// **CQRS 设计**：
///
/// 遵循 CQRS 原则，聚合根只包含核心身份标识和来源追踪字段。
/// 非核心属性存储在独立的补充数据表：
///
/// - `cat_venue_detail` - 出版信息、索引信息、OA 状态
/// - `cat_venue_stats` - 统计快照（发文量、引用量等）
/// - `cat_venue_apc` - 文章处理费信息
/// - `cat_venue_society` - 关联学术组织
///
/// **核心字段**：
///
/// | 字段 | 说明 | 不变量 |
/// |------|------|--------|
/// | venue_type | 载体类型 | 必填，创建后不可变 |
/// | display_name | 显示名称 | 必填 |
/// | provenance_code | 数据来源 | 必填，追踪首次导入来源 |
///
/// **索引说明**：
///
/// - 普通索引 `idx_venue_type`: 载体类型
/// - 普通索引 `idx_display_name`: 名称前缀
/// - 普通索引 `idx_provenance`: 数据来源
/// - 全文索引 `ft_display_name`: 名称全文检索
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue", autoResultMap = true)
public class VenueDO extends BaseDO {

  // ========================================
  // 核心属性（不变量验证所需）
  // ========================================

  /// 载体类型：JOURNAL/REPOSITORY/CONFERENCE/EBOOK_PLATFORM/BOOK_SERIES/METADATA/OTHER
  @TableField("venue_type")
  private String venueType;

  /// 载体显示名称（主名称）
  @TableField("display_name")
  private String displayName;

  // ========================================
  // 来源追踪（Provenance）
  // ========================================

  /// 数据来源代码：OPENALEX/PUBMED/CROSSREF/DOAJ/MANUAL
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
}
