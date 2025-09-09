package com.patra.registry.app.service;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.app.usecase.LiteratureProvenanceQueryUseCase;
import com.patra.registry.contract.query.port.LiteratureProvenanceQueryPort;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;
import com.patra.registry.contract.query.view.ProvenanceSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文献数据源应用服务
 */
@Service
@RequiredArgsConstructor
public class LiteratureProvenanceService implements LiteratureProvenanceQueryUseCase {
    
    private final LiteratureProvenanceQueryPort queryPort;

    /**
     * 分页查询文献数据源
     */
    public List<ProvenanceSummaryView> findAll() {
        return queryPort.findAll();
    }

    @Override
    public LiteratureProvenanceConfigView getConfigView(ProvenanceCode provenanceCode) {
        return queryPort.getConfigByProvenanceCode(provenanceCode);
    }
}
