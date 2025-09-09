package com.patra.registry.infra.persistence.repository;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.contract.query.port.LiteratureProvenanceQueryPort;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;
import com.patra.registry.contract.query.view.ProvenanceSummaryView;
import com.patra.registry.infra.mapstruct.LiteratureProvenanceConverter;
import com.patra.registry.infra.persistence.entity.LiteratureProvenanceDO;
import com.patra.registry.infra.persistence.mapper.LiteratureProvenanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class LiteratureProvenanceQueryPortImpl implements LiteratureProvenanceQueryPort {

    private final LiteratureProvenanceMapper provenanceMapper;
    private final LiteratureProvenanceConverter converter;


    @Override
    public List<ProvenanceSummaryView> findAll() {
        List<LiteratureProvenanceDO> provenances = provenanceMapper.selectProvSummaryAll();
        return converter.toSummaryView(provenances);
    }

    @Override
    public LiteratureProvenanceConfigView getConfigByProvenanceCode(ProvenanceCode provenanceCode) {
        return provenanceMapper.selectConfigByCode(provenanceCode.getCode());
    }
}
