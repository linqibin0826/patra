package com.patra.registry.contract.query.port;


import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;
import com.patra.registry.contract.query.view.ProvenanceSummaryView;
import com.patra.registry.contract.query.view.QueryCapabilityView;
import com.patra.registry.contract.query.view.ApiParamMappingView;
import com.patra.registry.contract.query.view.QueryRenderRuleView;

import java.util.List;

public interface LiteratureProvenanceQueryPort {

    List<ProvenanceSummaryView> findAll();

    /**
     * 根据数据源code获取配置
     * @param provenanceCode 数据源code
     * @return 数据源配置视图
     */
    LiteratureProvenanceConfigView getConfigByProvenanceCode(ProvenanceCode provenanceCode);

    /**
     * 根据数据源code获取查询能力集合
     * @param provenanceCode 数据源code
     * @return 查询能力视图列表
     */
    List<QueryCapabilityView> getQueryCapabilitiesByProvenanceCode(ProvenanceCode provenanceCode);

    /** 获取 API 参数映射集合 */
    List<ApiParamMappingView> getApiParamMappingsByProvenanceCode(ProvenanceCode provenanceCode);

    /** 获取查询渲染规则集合 */
    List<QueryRenderRuleView> getQueryRenderRulesByProvenanceCode(ProvenanceCode provenanceCode);
}
