package com.patra.registry.app.usecase;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;

public interface LiteratureProvenanceQueryUseCase {

    /**
     * 根据数据源代码获取配置
     * @param provenanceCode 数据源代码
     * @return 配置视图
     */
    LiteratureProvenanceConfigView getConfigView(ProvenanceCode provenanceCode);
}
