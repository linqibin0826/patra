package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 发布历史数据库实体,映射到表 `cat_publication_history`。
///
/// 表结构: 记录文献生命周期事件(投稿、接收、发表等),支持时序性保障。
///
/// 关键字段说明:
///
/// - `publication_id` 出版物 ID(外键:cat_publication.id)
///   - `event_type` 事件类型,CHECK 约束 13 个枚举值
///   - `event_date` 事件日期(DATE),映射为 Instant（UTC午夜）
///   - `order_num` 事件顺序号(同一文献内唯一)
///   - `is_public` 是否公开(0=否,1=是)
///   - `metadata` JSON 扩展数据字段
///
/// 时序性保障: event_date + order_num 双重保障策略,确保同一天事件的顺序。
///
/// 唯一约束: uk_history_order(publication_id, order_num),保证顺序号在同一文献内唯一。
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_publication_history", autoResultMap = true)
public class PublicationHistoryDO extends BaseDO {
  /// 出版物 ID(外键:cat_publication.id)
  @TableField("publication_id")
  private Long publicationId;

  /// 事件类型。
  ///
  /// CHECK 约束确保值在枚举范围内: Submitted/Received/Revised/Accepted/Rejected/Published Online/
  /// Published Print/Corrected/Retracted/Reinstated/Updated/Indexed/Archived。
  @TableField("event_type")
  private String eventType;

  /// 事件日期
  @TableField("event_date")
  private Instant eventDate;

  /// 日期精度(day/month/year)。
  ///
  /// CHECK 约束确保值在枚举范围内。
  @TableField("date_precision")
  private String datePrecision;

  /// 事件描述
  @TableField("description")
  private String description;

  /// 执行者/机构
  @TableField("actor")
  private String actor;

  /// 之前状态
  @TableField("previous_status")
  private String previousStatus;

  /// 新状态
  @TableField("new_status")
  private String newStatus;

  /// 事件顺序号(同一文献内唯一)
  @TableField("order_num")
  private Integer orderNum;

  /// 是否公开(0=否,1=是)
  @TableField("is_public")
  private Boolean isPublic;

  /// 事件元数据(JSON 格式)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
