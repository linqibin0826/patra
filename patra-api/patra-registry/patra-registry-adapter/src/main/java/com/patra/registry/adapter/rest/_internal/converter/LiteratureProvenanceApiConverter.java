package com.patra.registry.adapter.rest._internal.converter;

import com.patra.registry.api.rpc.dto.ApiParamMappingApiResp;
import com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp;
import com.patra.registry.api.rpc.dto.QueryCapabilityApiResp;
import com.patra.registry.api.rpc.dto.QueryRenderRuleApiResp;
import com.patra.registry.contract.query.view.ApiParamMappingView;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;
import com.patra.registry.contract.query.view.QueryCapabilityView;
import com.patra.registry.contract.query.view.QueryRenderRuleView;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

// 说明：

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface LiteratureProvenanceApiConverter {

    // ---- Config ----
    LiteratureProvenanceConfigApiResp toConfigApiResp(LiteratureProvenanceConfigView src);

    // ---- Capability ----
    QueryCapabilityApiResp toQueryCapabilityApiResp(QueryCapabilityView src);

    default List<QueryCapabilityApiResp> toQueryCapabilityApiRespList(List<QueryCapabilityView> src) {
        if (src == null || src.isEmpty()) {
            return List.of();
        }
        return src.stream().map(this::toQueryCapabilityApiResp).toList();
    }

    // ---- ApiParamMapping ----
    ApiParamMappingApiResp toApiParamMappingApiResp(ApiParamMappingView src);

    default List<ApiParamMappingApiResp> toApiParamMappingApiRespList(List<ApiParamMappingView> src) {
        if (src == null || src.isEmpty()) {
            return List.of();
        }
        return src.stream().map(this::toApiParamMappingApiResp).toList();
    }

    // ---- QueryRenderRule ----
    @Named("ruleView2Api")
    QueryRenderRuleApiResp ruleView2Api(QueryRenderRuleView src);

    @IterableMapping(qualifiedByName = "ruleView2Api")
    List<QueryRenderRuleApiResp> ruleViews2ApiList(List<QueryRenderRuleView> src);
}
