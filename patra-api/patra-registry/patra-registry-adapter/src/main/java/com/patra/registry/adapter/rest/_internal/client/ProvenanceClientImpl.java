package com.patra.registry.adapter.rest._internal.client;

import com.patra.registry.adapter.rest._internal.convertor.ProvenanceApiConvertor;
import com.patra.registry.api.rpc.client.ProvenanceClient;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceResp;
import com.patra.registry.app.service.ProvenanceConfigAppService;
import com.patra.registry.contract.query.view.provenance.ProvenanceConfigQuery;
import com.patra.registry.contract.query.view.provenance.ProvenanceQuery;
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
    private final ProvenanceApiConvertor convertor;

    @Override
    public List<ProvenanceResp> listProvenances() {
        return convertor.toResp(appService.listProvenances());
    }

    @Override
    public ProvenanceResp getProvenance(String code) {
        Optional<ProvenanceQuery> result = appService.findProvenance(code);
        return result.map(convertor::toResp).orElse(null);
    }

    @Override
    public ProvenanceConfigResp getConfiguration(String code,
                                                 String taskType,
                                                 String endpointName,
                                                 Instant at) {
        Optional<ProvenanceConfigQuery> result = appService.loadConfiguration(code, taskType, endpointName, at);
        return result.map(convertor::toResp).orElse(null);
    }
}
