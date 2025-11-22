package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/// 物质数据库实体,映射到表 `cat_substance`。
/// 
/// 表结构: 存储化学物质、药物、生物制品等信息,支持 CAS 号等注册号检索。
/// 
/// 关键字段说明:
/// 
/// - `registry_number` 注册号(如 CAS 号,格式:50-78-2),唯一索引 uk_registry
///   - `name` 物质名称(英文)
///   - `substance_class` 物质分类(枚举:chemical/drug/biological/enzyme/antibody/protein)
///   - `molecular_formula` 分子式(如 C9H8O4)
///   - `synonyms` 同义词列表(JSON 数组,多语言)
///   - `metadata` JSON 扩展数据字段
/// 
/// 索引说明:
/// 
/// - `uk_registry` 注册号唯一索引,支持 CAS 号精确查询(<5ms)
///   - `idx_name` 物质名称索引,支持按名称查询
///   - `idx_class` 物质分类索引,支持按分类筛选
/// 
/// @author linqibin
/// @since 0.2.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_substance", autoResultMap = true)
public class SubstanceDO extends BaseDO {
  /// 注册号(如 CAS 号,格式:50-78-2)
  @TableField("registry_number")
  private String registryNumber;

  /// 物质名称(英文)
  @TableField("name")
  private String name;

  /// 词表 ID(外部词表标识)
  @TableField("vocabulary_id")
  private String vocabularyId;

  /// 词表来源(如 CAS/EC/UNII/ChEBI/PubChem)
  @TableField("vocabulary_source")
  private String vocabularySource;

  /// 物质分类(枚举:chemical/drug/biological/enzyme/antibody/protein)
  @TableField("substance_class")
  private String substanceClass;

  /// 分子式(如 C9H8O4)
  @TableField("molecular_formula")
  private String molecularFormula;

  /// 同义词列表(JSON 数组,多语言)
  @TableField(value = "synonyms", typeHandler = JacksonTypeHandler.class)
  private JsonNode synonyms;

  /// 元数据(扩展字段,JSON 格式)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;

}
