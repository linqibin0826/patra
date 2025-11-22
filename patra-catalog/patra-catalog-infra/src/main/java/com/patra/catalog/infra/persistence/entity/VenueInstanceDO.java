package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/// 载体实例数据库实体,映射到表 `cat_venue_instance`。
/// 
/// 表结构: 存储载体的具体实例(期刊的卷期、书籍的版次、会议的届次)。
/// 
/// 关键字段说明:
/// 
/// - `venue_id` 载体 ID,外键关联 cat_venue.id
///   - `volume` 卷号(期刊专用)
///   - `issue` 期号(期刊专用)
///   - `edition` 版次(书籍专用)
///   - `publication_year` 出版年份(必填,用于冗余到主表)
///   - `conference_name` 会议名称(会议专用)
///   - `instance_metadata` JSON 扩展数据字段
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

  /// 会议开始日期(会议专用)
  @TableField("conference_start_date")
  private Instant conferenceStartDate;

  /// 会议结束日期(会议专用)
  @TableField("conference_end_date")
  private Instant conferenceEndDate;

  /// 会议地点(会议专用)
  @TableField("conference_location")
  private String conferenceLocation;

  /// 实例元数据(JSON 格式,灵活扩展)
  @TableField(value = "instance_metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode instanceMetadata;

}
