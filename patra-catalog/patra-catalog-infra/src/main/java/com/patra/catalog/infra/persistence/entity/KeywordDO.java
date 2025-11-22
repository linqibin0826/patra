package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 关键词数据库实体,映射到表 `cat_keyword`。
///
/// 表结构: 存储自由关键词,支持规范化去重和频次统计。
///
/// 关键字段说明:
///
/// - `term` 关键词原始形式
///   - `source` 来源(枚举:author/editor/indexer/pubmed)
///   - `normalized_term` 规范化词形(小写+去标点+去空格,用于去重)
///   - `frequency` 出现频次(被多少篇文献使用)
///   - `metadata` JSON 扩展数据字段
///
/// 索引说明:
///
/// - `idx_normalized` 规范化词形索引,支持去重查询
///   - `idx_frequency` 频次索引,支持热门关键词排序
///   - `idx_source_lang` 来源+语言复合索引,支持按来源和语言筛选
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_keyword", autoResultMap = true)
public class KeywordDO extends BaseDO {

  /// 关键词原始形式
  @TableField("term")
  private String term;

  /// 来源(枚举:author/editor/indexer/pubmed)
  @TableField("source")
  private String source;

  /// 语言代码(ISO 639-1,如 en/zh)
  @TableField("language")
  private String language;

  /// 规范化词形(小写+去标点+去空格,用于去重)
  @TableField("normalized_term")
  private String normalizedTerm;

  /// 出现频次(被多少篇文献使用)
  @TableField("frequency")
  private Integer frequency;

  /// 元数据(扩展字段,JSON 格式)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
