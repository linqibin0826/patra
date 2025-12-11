package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 载体实例数据库实体（独立聚合根），映射到表 `cat_venue_instance`。
///
/// **设计说明**：
///
/// VenueInstance 已提升为独立聚合根，通过 `venue_id` 外键关联 VenueAggregate。
/// 存储载体的具体实例（期刊的卷期、书籍的版次、会议的届次）。
///
/// **实例类型**：
///
/// | 类型 | 关键字段 | 示例 |
/// |------|----------|------|
/// | 期刊 | volume, issue | Nature Vol.612, No.5 |
/// | 书籍 | edition | 2nd Edition |
/// | 会议 | conferenceName, conferenceStartDate | AAAI 2024, Vancouver |
///
/// **索引说明**：
///
/// - 复合索引 `idx_venue_volume_issue`: 期刊实例查询
/// - 普通索引 `idx_publication_year`: 按年份筛选
/// - 普通索引 `idx_venue_id`: 按载体查询所有实例
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_instance", autoResultMap = true)
public class VenueInstanceDO extends BaseDO {
  /// 载体 ID(外键: cat_venue.id)
  @TableField("venue_id")
  private Long venueId;

  /// 卷号(如 "45", "2023")
  @TableField("volume")
  private String volume;

  /// 期号(如 "3", "Suppl 1")
  @TableField("issue")
  private String issue;

  /// 版次(书籍专用,如 "2nd Edition")
  @TableField("edition")
  private String edition;

  /// 出版年份(必填,用于冗余到主表)
  @TableField("publication_year")
  private Integer publicationYear;

  /// 出版月份(1-12,可能为空)
  @TableField("publication_month")
  private Integer publicationMonth;

  /// 出版日期(1-31,可能为空)
  @TableField("publication_day")
  private Integer publicationDay;

  /// 会议名称(会议专用)
  @TableField("conference_name")
  private String conferenceName;

  /// 会议开始日期（会议专用）
  @TableField("conference_start_date")
  private LocalDate conferenceStartDate;

  /// 会议结束日期（会议专用）
  @TableField("conference_end_date")
  private LocalDate conferenceEndDate;

  /// 会议地点(会议专用)
  @TableField("conference_location")
  private String conferenceLocation;

  /// 实例元数据(JSON 格式,灵活扩展)
  @TableField(value = "instance_metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode instanceMetadata;
}
