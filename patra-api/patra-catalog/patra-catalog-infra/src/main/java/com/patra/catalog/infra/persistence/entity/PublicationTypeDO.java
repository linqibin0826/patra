package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * 出版类型数据库实体,映射到表 {@code cat_publication_type}。
 *
 * <p>表结构: 存储文献出版类型(如期刊文章、综述、临床试验),支持层次结构。
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code type_code} 类型代码(英文,唯一标识),唯一索引 uk_type_code
 *   <li>{@code type_name} 类型名称(英文)
 *   <li>{@code parent_type} 父类型代码(自引用,支持层次)
 *   <li>{@code is_active} 是否有效(0=已废弃,1=有效)
 *   <li>{@code metadata} JSON 扩展数据字段
 * </ul>
 *
 * <p>索引说明:
 *
 * <ul>
 *   <li>{@code uk_type_code} 类型代码唯一索引,支持精确查询(<5ms)
 *   <li>{@code idx_parent} 父类型索引,支持查询子类型(递归查询)
 *   <li>{@code idx_active} 有效状态索引,筛选有效类型
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_publication_type", autoResultMap = true)
public class PublicationTypeDO extends BaseDO {
  /** 类型代码(英文,唯一标识) */
  @TableField("type_code")
  private String typeCode;

  /** 类型名称(英文) */
  @TableField("type_name")
  private String typeName;

  /** 描述说明 */
  @TableField("description")
  private String description;

  /** 词表来源(如 MEDLINE/EMBASE/CUSTOM) */
  @TableField("vocabulary_source")
  private String vocabularySource;

  /** 父类型代码(自引用,支持层次) */
  @TableField("parent_type")
  private String parentType;

  /** 是否有效(0=已废弃,1=有效) */
  @TableField("is_active")
  private Boolean isActive;

  /** 元数据(扩展字段,JSON 格式) */
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;

}
