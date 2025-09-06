package com.patra.registry.infra.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文献数据源数据对象
 * docref: /docs/schema/tables.inventory.md#reg_literature_provenance
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LiteratureProvenanceDO extends BaseDO {
    
    /**
     * 数据源名称;pubmed/epmc/openalex/crossref
     */
    private String name;
    
    /**
     * 数据源代码;简短标识符
     */
    private String code;
    
    /**
     * json数组,备注/变更说明
     */
    private JsonNode recordRemarks;
}
