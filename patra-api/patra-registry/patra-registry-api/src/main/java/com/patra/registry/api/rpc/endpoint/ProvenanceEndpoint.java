package com.patra.registry.api.rpc.endpoint;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.rpc.dto.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

public interface ProvenanceEndpoint {

    String BASE_PATH = "/_internal/provenances";

    @GetMapping(BASE_PATH + "/{code}/config")
    LiteratureProvenanceConfigApiResp getConfigByCode(@PathVariable("code") ProvenanceCode provenanceCode);

    /**
     * 暴露来源查询能力（QueryCapability）信息
     *
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


    /**
     * 一次性返回指定数据源 + operation 的规则快照
     * - 由 registry 服务端在同一读快照/同一事务中组装
     * - 返回包含 version/updatedAt，便于 ETag/If-None-Match 缓存
     */
    @GetMapping(BASE_PATH + "/{code}/snapshot/{operation}")
    ProvenanceExprConfigSnapshotApiResp getExprConfigSnapshot(@PathVariable("code") ProvenanceCode provenanceCode,
                                                              @PathVariable("operation") String operation);
}
