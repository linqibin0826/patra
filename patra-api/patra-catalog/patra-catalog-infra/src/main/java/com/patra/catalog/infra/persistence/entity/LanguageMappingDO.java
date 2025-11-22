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

/// 语言映射数据库实体,映射到表 `cat_language_mapping`。
///
/// 表结构: 原始语言值到标准语言代码的映射表,支持动态学习和人工验证
///
/// 关键字段说明:
///
/// - `raw_value` 原始语言值,唯一约束 uk_raw_value
///   - `standard_code` 标准语言代码(ISO 639-1)
///   - `confidence_score` 置信度(0-100),DECIMAL 映射为 BigDecimal
///   - `usage_count` 使用次数(每次应用层查询自增)
///   - `variant_forms` 变体形式(JSON 数组)
///   - `metadata` 映射元数据(灵活扩展)
///
/// @author linqibin
/// @since 0.5.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_language_mapping", autoResultMap = true)
public class LanguageMappingDO extends BaseDO {
  /// 原始语言值(唯一,如"eng","Chinese")
  @TableField("raw_value")
  private String rawValue;

  /// 标准语言代码(ISO 639-1,如"en","zh")
  @TableField("standard_code")
  private String standardCode;

  /// 基础语种(如"en","zh","ja")
  @TableField("base_language")
  private String baseLanguage;

  /// 英文名称(如"English","Chinese")
  @TableField("language_name_en")
  private String languageNameEn;

  /// 本地名称(如"English","中文","日本語")
  @TableField("language_name_native")
  private String languageNameNative;

  /// 映射来源:ISO_639/NLP_Inference/Manual/Similarity_Match
  @TableField("mapping_source")
  private String mappingSource;

  /// 置信度(0-100,如95.50)
  @TableField("confidence_score")
  private BigDecimal confidenceScore;

  /// 使用次数(每次应用层查询自增)
  @TableField("usage_count")
  private Integer usageCount;

  /// 是否已验证(0=未验证,1=已验证)
  @TableField("is_verified")
  private Boolean isVerified;

  /// 最后使用时间(UTC,微秒精度)
  @TableField("last_used")
  private Instant lastUsed;

  /// 变体形式(JSON数组)
  @TableField(value = "variant_forms", typeHandler = JacksonTypeHandler.class)
  private JsonNode variantForms;

  /// 映射元数据(灵活扩展)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
