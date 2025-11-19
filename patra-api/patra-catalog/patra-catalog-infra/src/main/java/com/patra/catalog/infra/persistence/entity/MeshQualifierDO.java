package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * MeSH 限定词表数据库实体，映射到表 {@code cat_mesh_qualifier}。
 *
 * <p>表结构：存储 MeSH 限定词，用于修饰主题词（如 "immunology" 限定 "Antibodies"）。
 *
 * <p>关键字段说明：
 *
 * <ul>
 *   <li>{@code ui} - 限定词唯一标识符（格式：Q000001-Q999999），唯一约束 uk_qualifier_ui
 *   <li>{@code name} - 限定词名称（英文）
 *   <li>{@code abbreviation} - 限定词缩写（如 DI, GE, IM）
 *   <li>{@code annotation} - 注释说明，TEXT 类型
 *   <li>{@code active_status} - 是否有效（0=已废弃，1=有效）
 *   <li>{@code mesh_version} - MeSH 版本年份（如 "2025"）
 * </ul>
 *
 * <p>索引说明：
 *
 * <ul>
 *   <li>uk_qualifier_ui - 限定词 UI 唯一索引，支持精确查询（&lt;5ms）
 *   <li>idx_name - 限定词名称索引，支持按名称查询
 * </ul>
 *
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_qualifier", autoResultMap = true)
public class MeshQualifierDO extends BaseDO {
  /** 限定词唯一标识符（格式：Q000001-Q999999） */
  @TableField("ui")
  private String ui;

  /** 限定词名称（英文） */
  @TableField("name")
  private String name;

  /** 限定词缩写（如 DI, GE, IM） */
  @TableField("abbreviation")
  private String abbreviation;

  /** 注释说明 */
  @TableField("annotation")
  private String annotation;

  /** 创建日期（格式：YYYYMMDD） */
  @TableField("date_created")
  private String dateCreated;

  /** 修订日期（格式：YYYYMMDD） */
  @TableField("date_revised")
  private String dateRevised;

  /** 确立日期（格式：YYYYMMDD） */
  @TableField("date_established")
  private String dateEstablished;

  /** 是否有效（0=已废弃，1=有效） */
  @TableField("active_status")
  private Boolean activeStatus;

  /** MeSH 版本年份（如 "2025"） */
  @TableField("mesh_version")
  private String meshVersion;

}
