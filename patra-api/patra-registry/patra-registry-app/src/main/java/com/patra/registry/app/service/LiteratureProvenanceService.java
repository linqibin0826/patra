package com.patra.registry.app.service;

import com.patra.registry.contract.query.port.LiteratureProvenanceQueryPort;
import com.patra.registry.contract.query.view.ProvenanceSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文献数据源应用服务
 */
@Service
@RequiredArgsConstructor
public class LiteratureProvenanceService {
    
    private final LiteratureProvenanceQueryPort queryPort;

    /**
     * 分页查询文献数据源
     */
    public List<ProvenanceSummaryView> findAll() {
        return queryPort.findAll();
    }

}
