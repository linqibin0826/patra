package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 补充对象数据库实体,映射到表 `cat_supplemental_object`。
///
/// 表结构: 管理补充材料(图表、数据集、代码等),支持访问控制和许可证管理。
///
/// 关键字段说明:
///
/// - `publication_id` 出版物 ID(外键:cat_publication.id)
///   - `object_type` 对象类型,CHECK 约束 9 个枚举值
///   - `file_size` 文件大小(字节)(BIGINT),映射为 Long
///   - `is_public` 是否公开(0=否,1=是)
///   - `available_date` 可用日期(延迟发布支持)(DATE),映射为 Instant（UTC午夜）
///   - `metadata` JSON 扩展数据字段
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_supplemental_object", autoResultMap = true)
public class SupplementalObjectDO extends BaseDO {
  /// 出版物 ID(外键:cat_publication.id)
  @TableField("publication_id")
  private Long publicationId;

  /// 对象类型。
  ///
  /// CHECK 约束确保值在枚举范围内: Figure/Table/Dataset/Code/Video/Audio/Document/Presentation/Other。
  @TableField("object_type")
  private String objectType;

  /// 内容类型(MIME,如"application/pdf")
  @TableField("content_type")
  private String contentType;

  /// 标题
  @TableField("title")
  private String title;

  /// 描述
  @TableField("description")
  private String description;

  /// 访问 URL
  @TableField("url")
  private String url;

  /// 文件名
  @TableField("file_name")
  private String fileName;

  /// 文件大小(字节)
  @TableField("file_size")
  private Long fileSize;

  /// 补充材料 DOI
  @TableField("doi")
  private String doi;

  /// 许可证(如"CC-BY","CC0")
  @TableField("license")
  private String license;

  /// 作者/贡献者
  @TableField("authors")
  private String authors;

  /// 顺序号(用于排序显示)
  @TableField("order_num")
  private Integer orderNum;

  /// 是否公开(0=否,1=是)
  @TableField("is_public")
  private Boolean isPublic;

  /// 可用日期(延迟发布支持)
  @TableField("available_date")
  private Instant availableDate;

  /// 对象元数据(JSON 格式)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
