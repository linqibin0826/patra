package com.patra.registry.infra.mapstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.registry.contract.query.view.ApiParamMappingView;
import com.patra.registry.infra.persistence.entity.SourceApiParamMappingDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApiParamMappingConverter {

    @Mapping(target = "notes", expression = "java(toNotesString(src.getNotes()))")
    ApiParamMappingView mapApiParam(SourceApiParamMappingDO src);

    default String toNotesString(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.isTextual() ? node.asText() : node.toString();
    }
}
