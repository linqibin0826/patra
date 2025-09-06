package com.patra.registry.infra.mapstruct;

import cn.hutool.core.collection.CollUtil;
import com.patra.registry.app.view.ProvenanceSummary;
import com.patra.registry.domain.model.aggregate.LiteratureProvenance;
import com.patra.registry.infra.persistence.entity.LiteratureProvenanceDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 文献数据源聚合 MapStruct 转换器
 * docref: 红线规范 - MapStruct仅接口，@Mapper(componentModel="spring")
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
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

    /**
     * DO 转换为文献数据源概要信息视图
     */
    ProvenanceSummary toSummary(LiteratureProvenanceDO literatureProvenanceDO);

    /**
     * 批量 DO 转换为文献数据源概要信息视图列表
     */
    default List<ProvenanceSummary> toSummaryList(List<LiteratureProvenanceDO> provenances) {
        if (CollUtil.isEmpty(provenances)) {
            return List.of();
        }
        return provenances.stream().map(this::toSummary).toList();
    }
}
