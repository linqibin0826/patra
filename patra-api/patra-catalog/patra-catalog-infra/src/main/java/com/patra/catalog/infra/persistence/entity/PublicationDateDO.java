package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * 日期信息数据库实体,映射到表 {@code cat_publication_date}。
 *
 * <p>表结构: 精确记录文献生命周期的各类日期,支持不完整日期表达
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code publication_id} 出版物ID(外键:cat_publication.id)
 *   <li>{@code date_type} 日期类型(Received/Accepted/Published等)
 *   <li>{@code date_value} 日期值(仅完整日期时填充),映射为 Instant（UTC午夜）
 *   <li>{@code year} 年份(必填,1900-2100)
 *   <li>{@code month} 月份(1-12,可能为空)
 *   <li>{@code day} 日期(1-31,可能为空)
 *   <li>{@code date_precision} 精度:year/month/day
 *   <li>{@code metadata} 日期元数据(灵活扩展)
 * </ul>
 *
 *
 * @author linqibin
 * @since 0.5.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_publication_date", autoResultMap = true)
public class PublicationDateDO extends BaseDO {
  /** 出版物ID(外键:cat_publication.id) */
  @TableField("publication_id")
  private Long publicationId;

  /** 日期类型:Received/Accepted/Published/Revised/Retracted/EPub/PPub/EntrezDate/Other */
  @TableField("date_type")
  private String dateType;

  /** 日期值(仅完整日期时填充) */
  @TableField("date_value")
  private Instant dateValue;

  /** 年份(必填,1900-2100) */
  @TableField("year")
  private Short year;

  /** 月份(1-12,可能为空) */
  @TableField("month")
  private Byte month;

  /** 日期(1-31,可能为空) */
  @TableField("day")
  private Byte day;

  /** 精度:year/month/day */
  @TableField("date_precision")
  private String datePrecision;

  /** 季节(如"Spring 2024","Q1 2023") */
  @TableField("season")
  private String season;

  /** 原始日期字符串(如"June 2023") */
  @TableField("date_string")
  private String dateString;

  /** 是否主要日期(0=否,1=是) */
  @TableField("is_primary")
  private Boolean isPrimary;

  /** 顺序号(同类型多个日期时使用) */
  @TableField("order_num")
  private Integer orderNum;

  /** 日期元数据(灵活扩展) */
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;

}
