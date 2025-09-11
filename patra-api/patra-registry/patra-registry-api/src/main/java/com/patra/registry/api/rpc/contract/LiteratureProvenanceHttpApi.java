package com.patra.registry.api.rpc.contract;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp;
import com.patra.registry.api.rpc.dto.QueryCapabilityApiResp;
import com.patra.registry.api.rpc.dto.ApiParamMappingApiResp;
import com.patra.registry.api.rpc.dto.QueryRenderRuleApiResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

public interface LiteratureProvenanceHttpApi {

    String BASE_PATH = "/_internal/literature-provenances";

    @GetMapping(BASE_PATH + "/{code}/config")
    LiteratureProvenanceConfigApiResp getConfigByCode(@PathVariable("code") ProvenanceCode provenanceCode);

    /**
     * 暴露来源查询能力（QueryCapability）信息
     * @param provenanceCode 数据源代码
     * @return 查询能力列表（按字段维度）
     */
    @GetMapping(BASE_PATH + "/{code}/query-capabilities")
    List<QueryCapabilityApiResp> getQueryCapabilitiesByCode(@PathVariable("code") ProvenanceCode provenanceCode);

    /**
     * 暴露 API 参数映射集合
     */
    @GetMapping(BASE_PATH + "/{code}/api-param-mappings")
    List<ApiParamMappingApiResp> getApiParamMappingsByCode(@PathVariable("code") ProvenanceCode provenanceCode);

    /**
     * 暴露查询渲染规则集合
     */
    @GetMapping(BASE_PATH + "/{code}/query-render-rules")
    List<QueryRenderRuleApiResp> getQueryRenderRulesByCode(@PathVariable("code") ProvenanceCode provenanceCode);
}
