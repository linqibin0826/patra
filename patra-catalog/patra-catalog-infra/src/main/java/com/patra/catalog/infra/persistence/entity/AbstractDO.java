package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 摘要数据库实体,映射到表 {@code cat_abstract}。
 *
 * <p>表结构: 独立存储文献摘要(大文本),支持结构化摘要和全文检索。
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code publication_id} 出版物 ID,外键关联 cat_publication.id,一对一关系,唯一索引 uk_publication
 *   <li>{@code plain_text} 纯文本摘要,TEXT 类型(最大 65535 字符)
 *   <li>{@code structured_sections} 结构化摘要段落(JSON 对象,如 BACKGROUND/METHODS/RESULTS/CONCLUSIONS)
 *   <li>{@code abstract_type} 摘要类型: structured/unstructured/graphical/none
 * </ul>
 *
 * <p>索引说明:
 *
 * <ul>
 *   <li>唯一索引 uk_publication: publication_id 保证一对一关系
 *   <li>全文索引 ft_plain_text: plain_text 支持中英文混合检索(ngram 解析器)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_abstract", autoResultMap = true)
public class AbstractDO extends BaseDO {
  /** 出版物 ID(外键: cat_publication.id,一对一关系) */
  @TableField("publication_id")
  private Long publicationId;

  /** 纯文本摘要(TEXT 类型,最大 65535 字符) */
  @TableField("plain_text")
  private String plainText;

  /** 结构化摘要段落(JSON 对象,如 BACKGROUND/METHODS/RESULTS/CONCLUSIONS) */
  @TableField(value = "structured_sections", typeHandler = JacksonTypeHandler.class)
  private JsonNode structuredSections;

  /** 版权信息/使用限制 */
  @TableField("copyright")
  private String copyright;

  /** 摘要类型: structured/unstructured/graphical/none */
  @TableField("abstract_type")
  private String abstractType;
}
