package com.patra.ingest.adapter.out.registry;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.adapter.out.registry.mapper.RegistryAclConverter;
import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;
import com.patra.ingest.app.port.outbound.PatraRegistryProvenancePort;
import com.patra.registry.api.rpc.client.ProvenanceClient;
import com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PatraRegistryProvenancePortImpl implements PatraRegistryProvenancePort {

    private final ProvenanceClient provenanceClient;
    private final RegistryAclConverter converter;


    @Override
    public ProvenanceConfigSnapshot getProvenanceConfigSnapshot(ProvenanceCode provenanceCode) {
        LiteratureProvenanceConfigApiResp configByCode = provenanceClient.getConfigByCode(provenanceCode);
        return converter.toProvenanceConfigSnapshot(configByCode);
    }
}
