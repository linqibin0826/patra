package com.patra.registry.contract.query.port;


import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;
import com.patra.registry.contract.query.view.ProvenanceSummaryView;

import java.util.List;

public interface LiteratureProvenanceQueryPort {

    List<ProvenanceSummaryView> findAll();

    /**
     * 根据数据源code获取配置
     * @param provenanceCode 数据源code
     * @return 数据源配置视图
     */
    LiteratureProvenanceConfigView getConfigByProvenanceCode(ProvenanceCode provenanceCode);
}
