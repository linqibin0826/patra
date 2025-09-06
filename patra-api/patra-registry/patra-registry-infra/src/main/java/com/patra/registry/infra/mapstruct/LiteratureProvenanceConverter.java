package com.patra.registry.infra.mapstruct;

import com.patra.registry.domain.model.aggregate.LiteratureProvenance;
import com.patra.registry.infra.persistence.entity.LiteratureProvenanceDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 文献数据源聚合 MapStruct 转换器
 * docref: 红线规范 - MapStruct仅接口，@Mapper(componentModel="spring")
 */
@Mapper(componentModel = "spring")
public interface LiteratureProvenanceConverter {
    
    /**
     * DO 转换为聚合（仅基础字段，复杂转换暂时忽略）
     */
    @Mapping(target = "recordRemarks", ignore = true)
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "queryCapability", ignore = true)
    @Mapping(target = "apiParamMappings", ignore = true)
    @Mapping(target = "queryRenderRules", ignore = true)
    LiteratureProvenance toDomain(LiteratureProvenanceDO literatureProvenanceDO);
    
    /**
     * 聚合转换为 DO（仅基础字段，复杂转换暂时忽略）
     */
    @Mapping(target = "recordRemarks", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdByName", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedByName", ignore = true)
    @Mapping(target = "ipAddress", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    LiteratureProvenanceDO toDO(LiteratureProvenance literatureProvenance);
}
