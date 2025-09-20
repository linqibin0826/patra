package com.patra.registry.infra.persistence.entity.expr;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 数据表 {@code reg_expr_field_dict} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_expr_field_dict")
public class RegExprFieldDictDO extends BaseDO {

    /** 统一内部字段键（业务稳定键）。 */
    @TableField("field_key")
    private String fieldKey;

    /** 字段展示名。 */
    @TableField("display_name")
    private String displayName;

    /** 字段说明。 */
    @TableField("description")
    private String description;

    /** 数据类型编码。 */
    @TableField("data_type_code")
    private String dataTypeCode;

    /** 基数编码（SINGLE/MULTI）。 */
    @TableField("cardinality_code")
    private String cardinalityCode;

    /** 是否允许对外暴露。 */
    @TableField("exposable")
    private Boolean exposable;

    /** 是否为日期字段。 */
    @TableField("is_date")
    private Boolean dateField;
}
