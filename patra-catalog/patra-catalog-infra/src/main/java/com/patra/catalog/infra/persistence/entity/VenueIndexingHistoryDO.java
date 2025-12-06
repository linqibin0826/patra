package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 载体索引历史数据库实体，映射到表 `cat_venue_indexing_history`。
///
/// 表结构：记录期刊在各索引数据库（MEDLINE、PMC 等）的索引历史，来源于 NLM Serfile。
///
/// 关键字段说明：
///
/// - `venue_id` 载体 ID，外键关联 cat_venue.id
/// - `indexing_source` 索引来源：MEDLINE/PMC/PubMed Central 等
/// - `currently_indexed` 当前是否被索引
/// - `indexing_treatment` 索引处理方式：FULL（全文）/SELECTIVE（选择性）
/// - `citation_subset` 引用子集：IM/AIM/N/D 等
/// - `start_year`/`end_year` 索引起止年份
///
/// 索引说明：
///
/// - 唯一索引 `uk_venue_indexing`: (venue_id, indexing_source, start_year) 防止重复
/// - 普通索引 `idx_venue_id`: venue_id 支持查询期刊的索引历史
/// - 普通索引 `idx_source_indexed`: (indexing_source, currently_indexed) 支持按来源筛选当前索引期刊
///
/// 使用场景：
///
/// 1. 从 Serfile 导入期刊的 IndexingHistoryList 数据
/// 2. 追踪期刊的 MEDLINE 索引状态变化
/// 3. 筛选当前被 MEDLINE 索引的期刊
/// 4. 期刊收录历史分析
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_indexing_history")
public class VenueIndexingHistoryDO extends BaseDO {

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  /// 索引来源：MEDLINE/PMC 等
  @TableField("indexing_source")
  private String indexingSource;

  /// 当前是否被索引（0=否，1=是）
  @TableField("currently_indexed")
  private Boolean currentlyIndexed;

  /// 索引处理方式：FULL/SELECTIVE
  @TableField("indexing_treatment")
  private String indexingTreatment;

  /// 引用子集：IM/AIM/N/D/H/K/T/E/S/X/B/C/F/Q
  @TableField("citation_subset")
  private String citationSubset;

  /// 索引开始年份
  @TableField("start_year")
  private Integer startYear;

  /// 索引开始卷号
  @TableField("start_volume")
  private String startVolume;

  /// 索引开始期号
  @TableField("start_issue")
  private String startIssue;

  /// 索引结束年份（仍在索引则为 null）
  @TableField("end_year")
  private Integer endYear;

  /// 索引结束卷号
  @TableField("end_volume")
  private String endVolume;

  /// 索引结束期号
  @TableField("end_issue")
  private String endIssue;
}
