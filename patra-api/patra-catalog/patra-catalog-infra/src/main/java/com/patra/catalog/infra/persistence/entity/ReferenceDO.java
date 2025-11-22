package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/// 参考文献数据库实体,映射到表 `cat_reference`。
/// 
/// 表结构: 管理文献引用关系,支持库内外引用双重关联。
/// 
/// 关键字段说明:
/// 
/// - `publication_id` 引用文献 ID(本文)(外键:cat_publication.id)
///   - `cited_publication_id` 被引文献 ID(如果在库中)(外键:cat_publication.id)
///   - `cited_pmid` 被引文献 PMID(库外引用)
///   - `cited_doi` 被引文献 DOI(库外引用)
///   - `reference_number` 引用编号(本文中的序号)
///   - `is_retracted` 是否已撤稿(0=否,1=是)
///   - `metadata` JSON 扩展数据字段
/// 
/// 双重关联设计:
/// 
/// - 库内引用(~30%): 使用 `cited_publication_id` 关联
///   - 库外引用(~70%): 使用 `cited_pmid`/`cited_doi` 保留完整信息
///   - 自动升级: 新文献入库时将库外引用升级为库内引用
/// 
/// 唯一约束: uk_reference_num(publication_id, reference_number),保证引用编号在同一文献内唯一。
/// 
/// @author linqibin
/// @since 0.4.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_reference", autoResultMap = true)
public class ReferenceDO extends BaseDO {
  /// 引用文献 ID(本文)(外键:cat_publication.id)
  @TableField("publication_id")
  private Long publicationId;

  /// 被引文献 ID(如果在库中)(外键:cat_publication.id)
  @TableField("cited_publication_id")
  private Long citedPublicationId;

  /// 被引文献 PMID(库外引用)
  @TableField("cited_pmid")
  private String citedPmid;

  /// 被引文献 DOI(库外引用)
  @TableField("cited_doi")
  private String citedDoi;

  /// 引用文本(原始引用格式)
  @TableField("citation_text")
  private String citationText;

  /// 文章标题
  @TableField("article_title")
  private String articleTitle;

  /// 来源期刊/书籍名称
  @TableField("source")
  private String source;

  /// 卷号
  @TableField("volume")
  private String volume;

  /// 期号
  @TableField("issue")
  private String issue;

  /// 页码(如"123-145")
  @TableField("pages")
  private String pages;

  /// 出版年份
  @TableField("year")
  private Short year;

  /// 作者列表(简化格式)
  @TableField("authors")
  private String authors;

  /// 引用类型。
/// 
/// CHECK 约束确保值在枚举范围内: Journal Article/Book/Book Chapter/Conference Paper/Thesis/Report/
/// Preprint/Web Page/Other。
  @TableField("reference_type")
  private String referenceType;

  /// 引用编号(本文中的序号)
  @TableField("reference_number")
  private Integer referenceNumber;

  /// 是否已撤稿(0=否,1=是)
  @TableField("is_retracted")
  private Boolean isRetracted;

  /// 引用元数据(JSON 格式)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
