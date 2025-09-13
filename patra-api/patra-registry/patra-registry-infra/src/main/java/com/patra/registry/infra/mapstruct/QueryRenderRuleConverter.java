package com.patra.registry.infra.mapstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.registry.contract.query.view.QueryRenderRuleView;
import com.patra.registry.infra.persistence.entity.SourceQueryRenderRuleDO;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface QueryRenderRuleConverter {

    @Mapping(target = "op", expression = "java(src.getOp()==null?null:src.getOp().getCode())")
    @Mapping(target = "matchType", expression = "java(src.getMatchType()==null?null:src.getMatchType().getCode())")
    @Mapping(target = "valueType", expression = "java(src.getValueType()==null?null:src.getValueType().getCode())")
    @Mapping(target = "emit", expression = "java(src.getEmit()==null?null:src.getEmit().getCode())")
    @Mapping(target = "params", expression = "java(toParamsString(src.getParams()))")
    QueryRenderRuleView toView(SourceQueryRenderRuleDO src);

    default List<QueryRenderRuleView> toViewList(List<SourceQueryRenderRuleDO> list) {
        if (list == null || list.isEmpty()) return java.util.List.of();
        List<QueryRenderRuleView> res = new ArrayList<>(list.size());
        for (SourceQueryRenderRuleDO it : list) res.add(toView(it));
        return res;
    }

    default String toParamsString(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.isTextual() ? node.asText() : node.toString();
    }
}
