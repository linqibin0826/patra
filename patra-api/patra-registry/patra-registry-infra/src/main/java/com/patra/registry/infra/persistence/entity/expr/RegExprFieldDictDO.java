package com.patra.registry.infra.persistence.entity.expr;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 数据库实体,映射到表 {@code reg_expr_field_dict}。
 *
 * <p>定义跨数据源共享的规范字段元数据。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_expr_field_dict")
public class RegExprFieldDictDO extends BaseDO {

  /** 规范字段键 that remains stable across environments. */
  @TableField("field_key")
  private String fieldKey;

  /** 可选的 display name surfaced in consoles. */
  @TableField("display_name")
  private String displayName;

  /** 详细描述 explaining field semantics. */
  @TableField("description")
  private String description;

  /** 数据类型代码 (DATE/DATETIME/NUMBER/TEXT/...). */
  @TableField("data_type_code")
  private String dataTypeCode;

  /** 基数代码 indicating single or multi-valued fields. */
  @TableField("cardinality_code")
  private String cardinalityCode;

  /** 指示 whether the field is exposable to clients. */
  @TableField("exposable")
  private Boolean exposable;

  /** 指示 whether the field should be treated as date-like. */
  @TableField("is_date")
  private Boolean dateField;
}
