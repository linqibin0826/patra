package com.patra.registry.adapter.rest._internal.converter;

import com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp;
import com.patra.registry.api.rpc.dto.QueryCapabilityApiResp;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;
import com.patra.registry.contract.query.view.QueryCapabilityView;
import com.patra.registry.api.rpc.dto.ApiParamMappingApiResp;
import com.patra.registry.api.rpc.dto.QueryRenderRuleApiResp;
import com.patra.registry.contract.query.view.ApiParamMappingView;
import com.patra.registry.contract.query.view.QueryRenderRuleView;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR // 字段漏映射就编译报错，避免线上惊喜
)
public interface LiteratureProvenanceApiConverter {

    LiteratureProvenanceConfigApiResp toConfigApiResp(LiteratureProvenanceConfigView src);

    QueryCapabilityApiResp toCapabilityApiResp(QueryCapabilityView src);

    java.util.List<QueryCapabilityApiResp> toQueryCapabilityApiResps(java.util.List<QueryCapabilityView> src);

    ApiParamMappingApiResp toApiParamMappingApiResp(ApiParamMappingView src);

    java.util.List<ApiParamMappingApiResp> toApiParamMappingApiResps(java.util.List<ApiParamMappingView> src);

    QueryRenderRuleApiResp toQueryRenderRuleApiResp(QueryRenderRuleView src);

    java.util.List<QueryRenderRuleApiResp> toQueryRenderRuleApiResps(java.util.List<QueryRenderRuleView> src);
}
