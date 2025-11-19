package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * 标识符数据库实体,映射到表 {@code cat_identifier}。
 *
 * <p>表结构: 管理出版物的多种类型标识符(PMID、DOI、PMC、PII、arXiv 等)。
 *
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code publication_id} 出版物 ID,外键关联 cat_publication.id
 *   <li>{@code type} 标识符类型: pmid/doi/pmc/pii/arxiv 等
 *   <li>{@code value} 标识符值(如 "38123456", "10.1038/nature12345")
 *   <li>{@code source} 标识符来源(如 "PubMed", "Crossref", "Manual")
 * </ul>
 *
 * <p>索引说明:
 *
 * <ul>
 *   <li>复合索引 idx_pub_type: (publication_id, type) 支持查询某文献的某类型标识符
 *   <li>复合索引 idx_type_value: (type, value) 支持按标识符查询文献
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_identifier")
public class IdentifierDO extends BaseDO {
  /** 出版物 ID(外键: cat_publication.id) */
  @TableField("publication_id")
  private Long publicationId;

  /** 标识符类型: pmid/doi/pmc/pii/arxiv 等 */
  @TableField("type")
  private String type;

  /** 标识符值(如 "38123456", "10.1038/nature12345") */
  @TableField("value")
  private String value;

  /** 标识符来源(如 "PubMed", "Crossref", "Manual") */
  @TableField("source")
  private String source;

}
