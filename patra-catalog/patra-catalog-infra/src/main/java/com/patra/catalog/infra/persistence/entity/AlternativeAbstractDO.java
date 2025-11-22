package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 其他语言摘要数据库实体,映射到表 `cat_alternative_abstract`。
///
/// 表结构: 管理文献摘要的多语言版本(官方翻译、专业翻译、机器翻译)
///
/// 关键字段说明:
///
/// - `publication_id` 出版物ID(外键:cat_publication.id)
///   - `abstract_id` 主摘要ID(外键:cat_abstract.id,关联原摘要)
///   - `language_code` 语言代码(ISO 639-1),唯一约束 uk_abstract_lang
///   - `plain_text` 纯文本摘要(TEXT 类型,最大65535字符)
///   - `structured_sections` 结构化摘要段落(JSON对象)
///   - `is_official` 是否官方翻译
///   - `metadata` 翻译元数据(灵活扩展)
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_alternative_abstract", autoResultMap = true)
public class AlternativeAbstractDO extends BaseDO {
  /// 出版物ID(外键:cat_publication.id)
  @TableField("publication_id")
  private Long publicationId;

  /// 主摘要ID(外键:cat_abstract.id,关联原摘要)
  @TableField("abstract_id")
  private Long abstractId;

  /// 语言代码(ISO 639-1,如"zh-CN","ja")
  @TableField("language_code")
  private String languageCode;

  /// 语言名称(如"Chinese","Japanese")
  @TableField("language_name")
  private String languageName;

  /// 纯文本摘要(最大65535字符)
  @TableField("plain_text")
  private String plainText;

  /// 结构化摘要段落(JSON对象)
  @TableField(value = "structured_sections", typeHandler = JacksonTypeHandler.class)
  private JsonNode structuredSections;

  /// 翻译类型:Official/Professional/Machine/Community
  @TableField("translation_type")
  private String translationType;

  /// 译者姓名或机构
  @TableField("translator")
  private String translator;

  /// 翻译日期
  @TableField("translation_date")
  private Instant translationDate;

  /// 质量级别:Excellent/Good/Fair/Poor
  @TableField("quality_level")
  private String qualityLevel;

  /// 是否官方翻译(0=否,1=是)
  @TableField("is_official")
  private Boolean isOfficial;

  /// 顺序号(同一语言多个翻译时排序)
  @TableField("order_num")
  private Integer orderNum;

  /// 翻译元数据(灵活扩展)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
