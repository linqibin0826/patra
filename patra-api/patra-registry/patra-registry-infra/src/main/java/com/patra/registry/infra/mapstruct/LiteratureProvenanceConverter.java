package com.patra.registry.infra.mapstruct;

import cn.hutool.core.collection.CollUtil;
import com.patra.registry.contract.query.view.ProvenanceSummaryView;
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

    /**w
     * DO 转换为文献数据源概要信息视图
     */
    @Mapping(target = "code", expression = "java(literatureProvenanceDO.getCode().name())")
    ProvenanceSummaryView toSummary(LiteratureProvenanceDO literatureProvenanceDO);

    /**
     * 批量 DO 转换为文献数据源概要信息视图列表
     */
    default List<ProvenanceSummaryView> toSummaryView(List<LiteratureProvenanceDO> provenances) {
        if (CollUtil.isEmpty(provenances)) {
            return List.of();
        }
        return provenances.stream().map(this::toSummary).toList();
    }
}
