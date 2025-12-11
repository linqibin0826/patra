package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// APC 信息数据库实体（CQRS 补充数据），映射到表 `cat_venue_apc`。
///
/// **设计说明**：
///
/// 与 `cat_venue` 表为 1:1 关系，通过 `venue_id` 唯一索引保证。
/// 存储文章处理费（Article Processing Charge）信息。
///
/// **数据来源**：OpenAlex、DOAJ
///
/// **字段说明**：
///
/// - `apc_usd`：标准化的美元价格，便于比较和排序
/// - `apc_prices`：JSON 数组，包含不同货币的价格
///
/// **apc_prices 格式示例**：
///
/// ```json
/// [
///   {"price": 3000, "currency": "USD"},
///   {"price": 2500, "currency": "EUR"},
///   {"price": 22000, "currency": "CNY"}
/// ]
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_apc", autoResultMap = true)
public class VenueApcDO extends BaseDO {

  // ========================================
  // 关联信息
  // ========================================

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  // ========================================
  // APC 信息
  // ========================================

  /// 文章处理费（美元）
  @TableField("apc_usd")
  private Integer apcUsd;

  /// APC 费用列表（JSON 数组，含不同货币价格）
  @TableField(value = "apc_prices", typeHandler = JacksonTypeHandler.class)
  private JsonNode apcPrices;
}
