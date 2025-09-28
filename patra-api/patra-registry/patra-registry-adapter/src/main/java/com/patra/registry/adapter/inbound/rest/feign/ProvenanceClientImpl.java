package com.patra.registry.adapter.inbound.rest.feign;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.adapter.inbound.rest.feign.converter.ProvenanceApiConverter;
import com.patra.registry.api.rpc.client.ProvenanceClient;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceResp;
import com.patra.registry.app.service.ProvenanceConfigAppService;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Provenance 内部 API 实现。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ProvenanceClientImpl implements ProvenanceClient {

    private final ProvenanceConfigAppService appService;
    private final ProvenanceApiConverter converter;

    @Override
    public List<ProvenanceResp> listProvenances() {
        return converter.toResp(appService.listProvenances());
    }

    @Override
    public ProvenanceResp getProvenance(ProvenanceCode code) {
        Optional<ProvenanceQuery> result = appService.findProvenance(code);
        if (result.isEmpty()) {
            throw new com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException(
                    "Provenance not found: code=" + code);
        }
        return converter.toResp(result.get());
    }

    @Override
    public ProvenanceConfigResp getConfiguration(ProvenanceCode code,
                                                 String taskType,
                                                 String endpointName,
                                                 Instant at) {
        Optional<ProvenanceConfigQuery> result = appService.loadConfiguration(code, taskType, endpointName, at);
        if (result.isEmpty()) {
            throw new com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException(
                    "Provenance configuration not found: code=" + code + ", taskType=" + taskType + ", endpoint=" + endpointName);
        }
        return converter.toResp(result.get());
    }
}
