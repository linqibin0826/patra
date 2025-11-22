package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 机构数据库实体,映射到表 `cat_affiliation`。
///
/// 表结构: 存储机构信息,支持多种国际标识符,实现机构标准化和去重。
///
/// 关键字段说明:
///
/// - `ror_id` ROR 标识符,唯一约束 uk_ror
///   - `grid_id` GRID 标识符,唯一约束 uk_grid
///   - `dedup_key` 复合去重键,应用层计算 MD5 哈希
///   - `metadata` JSON 扩展数据字段
///
/// @author linqibin
/// @since 0.3.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_affiliation", autoResultMap = true)
public class AffiliationDO extends BaseDO {

  /// 机构名称（标准化后）
  @TableField("name")
  private String name;

  /// 原始名称（外部采集,未标准化）
  @TableField("original_name")
  private String originalName;

  /// 部门/科室（如"Department of Medicine"）
  @TableField("department")
  private String department;

  /// 分部/分院（如"School of Medicine"）
  @TableField("division")
  private String division;

  /// 科/组（如"Cardiology Section"）
  @TableField("section")
  private String section;

  /// 城市（如"Boston"）
  @TableField("city")
  private String city;

  /// 州/省（如"Massachusetts","广东"）
  @TableField("state_province")
  private String stateProvince;

  /// 国家（ISO 3166-1 alpha-3,如"USA","CHN"）
  @TableField("country")
  private String country;

  /// 邮政编码（如"02115"）
  @TableField("postal_code")
  private String postalCode;

  /// ROR 标识符（如"https://ror.org/03vek6s52"）
  @TableField("ror_id")
  private String rorId;

  /// GRID 标识符（如"grid.38142.3c"）
  @TableField("grid_id")
  private String gridId;

  /// ISNI 标识符（如"0000 0004 1936 8948"）
  @TableField("isni")
  private String isni;

  /// Ringgold ID（如"1812"）
  @TableField("ringgold_id")
  private String ringgoldId;

  /// 上级机构（如"Harvard University"）
  @TableField("parent_affiliation")
  private String parentAffiliation;

  /// 机构类型（如"Education","Healthcare","Company"）
  @TableField("affiliation_type")
  private String affiliationType;

  /// 复合去重键（应用层计算,MD5哈希）
  @TableField("dedup_key")
  private String dedupKey;

  /// 机构元数据（JSON 格式）
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
