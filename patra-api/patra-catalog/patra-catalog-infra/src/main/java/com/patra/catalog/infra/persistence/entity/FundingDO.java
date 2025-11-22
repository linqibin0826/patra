package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 资助信息数据库实体,映射到表 `cat_funding`。
///
/// 表结构: 管理研究资金来源和项目信息,通过去重策略避免重复存储。
///
/// 关键字段说明:
///
/// - `dedup_key` 去重键(MD5 哈希,基于 agency_name + grant_id),唯一约束 uk_dedup_key
///   - `funder_id` Crossref Funder Registry ID,支持标准化查询
///   - `ror_id` ROR(Research Organization Registry)标识符
///   - `amount` 资助金额(DECIMAL 20,2),映射为 BigDecimal
///   - `start_date` 和 `end_date` 开始/结束日期(DATE),映射为 Instant（UTC午夜）
///   - `funding_type` 资助类型,CHECK 约束 6 个枚举值
///   - `metadata` JSON 扩展数据字段
///
/// @author linqibin
/// @since 0.4.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_funding", autoResultMap = true)
public class FundingDO extends BaseDO {
  /// 资助机构名称
  @TableField("agency_name")
  private String agencyName;

  /// 机构缩写(如"NIH","NSF")
  @TableField("agency_abbreviation")
  private String agencyAbbreviation;

  /// 资助国家(ISO 3166-1 alpha-3,如 USA/CHN)
  @TableField("country")
  private String country;

  /// 资助编号/项目编号
  @TableField("grant_id")
  private String grantId;

  /// 项目缩写
  @TableField("grant_acronym")
  private String grantAcronym;

  /// 项目名称
  @TableField("grant_name")
  private String grantName;

  /// 资助类型(Government/Foundation/Corporate/University/Non-profit/Other)。
  ///
  /// CHECK 约束确保值在枚举范围内。
  @TableField("funding_type")
  private String fundingType;

  /// 资助金额
  @TableField("amount")
  private BigDecimal amount;

  /// 货币类型(如"USD","EUR","CNY")
  @TableField("currency")
  private String currency;

  /// 开始日期
  @TableField("start_date")
  private Instant startDate;

  /// 结束日期
  @TableField("end_date")
  private Instant endDate;

  /// Crossref Funder Registry ID
  @TableField("funder_id")
  private String funderId;

  /// ROR(Research Organization Registry)标识符
  @TableField("ror_id")
  private String rorId;

  /// 去重键(MD5 哈希,基于 agency_name + grant_id)
  @TableField("dedup_key")
  private String dedupKey;

  /// 资助元数据(JSON 格式)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
