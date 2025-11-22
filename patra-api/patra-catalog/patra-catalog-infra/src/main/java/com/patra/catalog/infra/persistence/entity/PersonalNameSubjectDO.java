package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 人物主题数据库实体,映射到表 `cat_personal_name_subject`。
///
/// 表结构: 存储文献的主题人物信息（传记类、历史类、纪念类文献）。
///
/// 关键字段说明:
///
/// - `publication_id` 出版物ID,外键 cat_publication.id
///   - `subject_type` 主题类型（如"biography","history","memorial"）
///   - `identifier` 人物标识符（如 VIAF ID, Wikidata ID）
///   - `metadata` JSON 扩展数据字段
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_personal_name_subject", autoResultMap = true)
public class PersonalNameSubjectDO extends BaseDO {
  /// 出版物ID（外键:cat_publication.id）
  @TableField("publication_id")
  private Long publicationId;

  /// 姓（Last Name/Family Name）
  @TableField("last_name")
  private String lastName;

  /// 名（First Name/Given Name）
  @TableField("fore_name")
  private String foreName;

  /// 姓名缩写（如"J.K."）
  @TableField("initials")
  private String initials;

  /// 后缀/头衔（如"Jr.","King","Emperor"）
  @TableField("suffix")
  private String suffix;

  /// 生卒年代（如"1820-1910","c. 460 BC - c. 370 BC"）
  @TableField("dates")
  private String dates;

  /// 人物描述（简短介绍）
  @TableField("description")
  private String description;

  /// 主题类型（如"biography","history","memorial"）
  @TableField("subject_type")
  private String subjectType;

  /// 人物标识符（如 VIAF ID, Wikidata ID）
  @TableField("identifier")
  private String identifier;

  /// 顺序号（多个主题人物时排序）
  @TableField("order_num")
  private Integer orderNum;

  /// 人物元数据（JSON 格式）
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
