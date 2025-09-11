package com.patra.registry.infra.mapstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.registry.contract.query.view.ApiParamMappingView;
import com.patra.registry.infra.persistence.entity.SourceApiParamMappingDO;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApiParamMappingConverter {

    @Mapping(target = "notes", expression = "java(toNotesString(src.getNotes()))")
    ApiParamMappingView toView(SourceApiParamMappingDO src);

    default List<ApiParamMappingView> toViewList(List<SourceApiParamMappingDO> list) {
        if (list == null || list.isEmpty()) return java.util.List.of();
        List<ApiParamMappingView> res = new ArrayList<>(list.size());
        for (SourceApiParamMappingDO it : list) res.add(toView(it));
        return res;
    }

    default String toNotesString(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.isTextual() ? node.asText() : node.toString();
    }
}
