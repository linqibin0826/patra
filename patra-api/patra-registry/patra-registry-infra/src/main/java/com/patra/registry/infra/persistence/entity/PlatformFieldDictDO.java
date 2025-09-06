package com.patra.registry.infra.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.registry.domain.model.enums.Cardinality;
import com.patra.registry.domain.model.enums.DataType;
import com.patra.registry.domain.model.enums.DateType;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 平台字段字典数据对象
 * docref: /docs/schema/tables.inventory.md#reg_plat_field_dict
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PlatformFieldDictDO extends BaseDO {

    
    /**
     * 平台统一字段键（小写蛇形，如 pub_date/title_abstract）
     */
    private String fieldKey;
    
    /**
     * 数据类型
     */
    private DataType dataType;
    
    /**
     * 基数：单值/多值
     */
    private Cardinality cardinality;
    
    /**
     * 是否日期字段（DateLens 判定用）
     */
    private Boolean isDate;
    
    /**
     * 仅日期类使用的 datetype 映射
     */
    private DateType datetype;
    
    /**
     * json 数组，备注/变更说明
     */
    private JsonNode recordRemarks;
}
