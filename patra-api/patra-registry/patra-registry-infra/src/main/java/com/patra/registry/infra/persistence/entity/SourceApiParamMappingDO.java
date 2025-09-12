package com.patra.registry.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 数据源API参数映射数据对象
 * docref: /docs/schema/tables.inventory.md#reg_source_api_param_mapping
 */
@Data
@SuperBuilder
@NoArgsConstructor
@TableName(value = "reg_source_api_param_mapping", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
public class SourceApiParamMappingDO extends BaseDO {

    
    /**
     * 逻辑外键→reg_literature_provenance.id
     */
    private Long literatureProvenanceId;
    
    /**
     * 操作名：search/fetch/lookup…
     */
    private String operation;
    
    /**
     * 标准键（统一内部语义键）
     */
    private String stdKey;
    
    /**
     * 供应商参数名（如 term/retmax/retstart）
     */
    private String providerParam;
    
    /**
     * 可选转换函数（如 toExclusiveMinus1d）
     */
    private String transform;
    
    /**
     * 备注/补充说明
     */
    private JsonNode notes;

}
