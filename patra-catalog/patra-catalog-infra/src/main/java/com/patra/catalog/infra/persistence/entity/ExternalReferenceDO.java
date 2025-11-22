package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 外部引用数据库实体,映射到表 `cat_external_reference`。
///
/// 表结构: 管理外部数据库引用(基因库、临床试验、数据集等),与参考文献分离。
///
/// 关键字段说明:
///
/// - `publication_id` 出版物 ID(外键:cat_publication.id)
///   - `database_name` 数据库名称(如"GenBank","ClinicalTrials.gov")
///   - `accession_number` 登录号/访问号
///   - `access_date` 访问日期(最后验证日期)(DATE),映射为 Instant（UTC午夜）
///   - `metadata` JSON 扩展数据字段
///
/// 唯一约束: uk_external_ref(publication_id, database_name, accession_number),防止重复引用。
///
/// @author linqibin
/// @since 0.4.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_external_reference", autoResultMap = true)
public class ExternalReferenceDO extends BaseDO {
  /// 出版物 ID(外键:cat_publication.id)
  @TableField("publication_id")
  private Long publicationId;

  /// 数据库名称(如"GenBank","ClinicalTrials.gov")
  @TableField("database_name")
  private String databaseName;

  /// 数据库类别(如"Genomic","Clinical Trial")
  @TableField("database_category")
  private String databaseCategory;

  /// 登录号/访问号
  @TableField("accession_number")
  private String accessionNumber;

  /// 链接地址(完整 URL)
  @TableField("url")
  private String url;

  /// 引用类型(描述性)
  @TableField("reference_type")
  private String referenceType;

  /// 描述信息
  @TableField("description")
  private String description;

  /// 访问日期(最后验证日期)
  @TableField("access_date")
  private Instant accessDate;

  /// 顺序号(用于排序显示)
  @TableField("order_num")
  private Integer orderNum;

  /// 外部引用元数据(JSON 格式)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
