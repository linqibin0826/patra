package com.patra.registry.infra.mapstruct;

import com.patra.registry.contract.query.view.ProvenanceSummaryView;
import com.patra.registry.infra.persistence.entity.ProvenanceDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 文献数据源聚合 MapStruct 转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface LiteratureProvenanceConverter {

    /**w
     * DO 转换为文献数据源概要信息视图
     */
    @Mapping(target = "code", expression = "java(literatureProvenanceDO.getCode().getCode())")
    ProvenanceSummaryView toSummary(ProvenanceDO provenanceDO);
}
