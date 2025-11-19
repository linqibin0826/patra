package com.patra.catalog.infra.persistence.entity;

import java.time.Instant;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 关键词数据库实体,映射到表 {@code cat_keyword}。
 *
 * <p>表结构: 存储自由关键词,支持规范化去重和频次统计。
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code term} 关键词原始形式
 *   <li>{@code source} 来源(枚举:author/editor/indexer/pubmed)
 *   <li>{@code normalized_term} 规范化词形(小写+去标点+去空格,用于去重)
 *   <li>{@code frequency} 出现频次(被多少篇文献使用)
 *   <li>{@code metadata} JSON 扩展数据字段
 * </ul>
 *
 * <p>索引说明:
 *
 * <ul>
 *   <li>{@code idx_normalized} 规范化词形索引,支持去重查询
 *   <li>{@code idx_frequency} 频次索引,支持热门关键词排序
 *   <li>{@code idx_source_lang} 来源+语言复合索引,支持按来源和语言筛选
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_keyword", autoResultMap = true)
public class KeywordDO extends BaseDO {

  /** 关键词原始形式 */
  @TableField("term")
  private String term;

  /** 来源(枚举:author/editor/indexer/pubmed) */
  @TableField("source")
  private String source;

  /** 语言代码(ISO 639-1,如 en/zh) */
  @TableField("language")
  private String language;

  /** 规范化词形(小写+去标点+去空格,用于去重) */
  @TableField("normalized_term")
  private String normalizedTerm;

  /** 出现频次(被多少篇文献使用) */
  @TableField("frequency")
  private Integer frequency;

  /** 元数据(扩展字段,JSON 格式) */
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;

}
