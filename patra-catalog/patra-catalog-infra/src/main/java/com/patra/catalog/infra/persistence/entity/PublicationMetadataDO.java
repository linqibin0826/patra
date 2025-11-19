package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 元数据数据库实体,映射到表 {@code cat_publication_metadata}。
 *
 * <p>表结构: 独立管理文献的元数据信息(索引状态、质量评分、数据溯源),与 cat_publication 一对一关系
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code publication_id} 出版物ID(外键:cat_publication.id,一对一关系),唯一约束 uk_pub_metadata
 *   <li>{@code indexing_status} 索引状态(Pending/Indexed/MEDLINE等)
 *   <li>{@code data_source} 数据来源(PubMed/EPMC/Crossref等)
 *   <li>{@code quality_score} 质量评分(A/B/C/D/F)
 *   <li>{@code validation_errors} 验证错误(JSON数组)
 *   <li>{@code ext_metadata} 扩展元数据(灵活扩展)
 * </ul>
 *
 *
 * @author linqibin
 * @since 0.5.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_publication_metadata", autoResultMap = true)
public class PublicationMetadataDO extends BaseDO {

  /** 出版物ID(外键:cat_publication.id,一对一关系) */
  @TableField("publication_id")
  private Long publicationId;

  /** 索引状态:Pending/Indexed/MEDLINE/PubMed-not-MEDLINE/OLDMEDLINE/In-Data-Review/In-Process/Failed */
  @TableField("indexing_status")
  private String indexingStatus;

  /** 索引方法:Automated/Curated/In-Data-Review */
  @TableField("indexing_method")
  private String indexingMethod;

  /** 索引日期 */
  @TableField("indexed_date")
  private Instant indexedDate;

  /** 数据来源:PubMed/EPMC/Crossref/Manual/Other */
  @TableField("data_source")
  private String dataSource;

  /** 导入批次标识(如"2025-01-18_PUBMED") */
  @TableField("import_batch")
  private String importBatch;

  /** 导入日期 */
  @TableField("import_date")
  private Instant importDate;

  /** 质量评分:A/B/C/D/F */
  @TableField("quality_score")
  private String qualityScore;

  /** 完整性评分:A/B/C/D/F */
  @TableField("completeness_score")
  private String completenessScore;

  /** 是否有全文(0=否,1=是) */
  @TableField("has_full_text")
  private Boolean hasFullText;

  /** 全文链接 */
  @TableField("full_text_url")
  private String fullTextUrl;

  /** 审核状态:Pending/Reviewed/Rejected/Approved */
  @TableField("review_status")
  private String reviewStatus;

  /** 审核日期 */
  @TableField("review_date")
  private Instant reviewDate;

  /** 审核人姓名 */
  @TableField("reviewer")
  private String reviewer;

  /** 验证错误(JSON数组) */
  @TableField(value = "validation_errors", typeHandler = JacksonTypeHandler.class)
  private JsonNode validationErrors;

  /** 处理注释(JSON数组) */
  @TableField(value = "processing_notes", typeHandler = JacksonTypeHandler.class)
  private JsonNode processingNotes;

  /** 扩展元数据(灵活扩展) */
  @TableField(value = "ext_metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode extMetadata;
  // - record_remarks (JSON 备注/变更日志)
  // - version (乐观锁版本号)
  // - ip_address (请求者IP)
  // - created_at (创建时间)
  // - created_by (创建人ID)
  // - created_by_name (创建人姓名)
  // - updated_at (更新时间)
  // - updated_by (更新人ID)
  // - updated_by_name (更新人姓名)
  // - deleted (软删除标志)

}
