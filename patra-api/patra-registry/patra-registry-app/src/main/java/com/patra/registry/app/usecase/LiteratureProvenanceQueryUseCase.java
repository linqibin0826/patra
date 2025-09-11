package com.patra.registry.app.usecase;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;
import com.patra.registry.contract.query.view.QueryCapabilityView;
import com.patra.registry.contract.query.view.ApiParamMappingView;
import com.patra.registry.contract.query.view.QueryRenderRuleView;

public interface LiteratureProvenanceQueryUseCase {

    /**
     * 根据数据源代码获取配置
     * @param provenanceCode 数据源代码
     * @return 配置视图
     */
    LiteratureProvenanceConfigView getConfigView(ProvenanceCode provenanceCode);

    /**
     * 根据数据源代码获取查询能力集合
     * @param provenanceCode 数据源代码
     * @return 查询能力视图列表
     */
    java.util.List<QueryCapabilityView> getQueryCapabilities(ProvenanceCode provenanceCode);

    /** 获取 API 参数映射集合 */
    java.util.List<ApiParamMappingView> getApiParamMappings(ProvenanceCode provenanceCode);

    /** 获取查询渲染规则集合 */
    java.util.List<QueryRenderRuleView> getQueryRenderRules(ProvenanceCode provenanceCode);
}
