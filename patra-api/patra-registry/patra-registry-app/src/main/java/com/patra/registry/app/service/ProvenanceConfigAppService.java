package com.patra.registry.app.service;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.app.mapping.ProvenanceQueryAssembler;
import com.patra.registry.contract.query.view.provenance.ProvenanceConfigQuery;
import com.patra.registry.contract.query.view.provenance.ProvenanceQuery;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.port.ProvenanceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Provenance 配置查询应用服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvenanceConfigAppService {

    private final ProvenanceConfigRepository repository;
    private final ProvenanceQueryAssembler assembler;

    /** 查询所有来源元数据。 */
    public List<ProvenanceQuery> listProvenances() {
        return repository.findAllProvenances().stream()
                .map(assembler::toQuery)
                .toList();
    }

    /** 按编码查询来源元数据。 */
    public Optional<ProvenanceQuery> findProvenance(ProvenanceCode provenanceCode) {
        return repository.findProvenanceByCode(provenanceCode)
                .map(assembler::toQuery);
    }

    /**
     * 加载来源在指定任务与端点下的聚合配置。
     */
    public Optional<ProvenanceConfigQuery> loadConfiguration(ProvenanceCode provenanceCode,
                                                             String taskType,
                                                             String endpointName,
                                                             Instant at) {
        Optional<Provenance> provenanceOpt = repository.findProvenanceByCode(provenanceCode);
        if (provenanceOpt.isEmpty()) {
            log.error("Provenance not found: code={}", provenanceCode);
            return Optional.empty();
        }

        Provenance provenance = provenanceOpt.get();
        Optional<ProvenanceConfiguration> configuration = repository.loadConfiguration(
                provenance.id(), taskType, endpointName, at != null ? at : Instant.now());

        return configuration.map(assembler::toQuery);
    }
}
