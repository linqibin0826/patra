package com.patra.registry.adapter.rest._internal.client;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.adapter.rest._internal.converter.LiteratureProvenanceApiConverter;
import com.patra.registry.api.rpc.contract.LiteratureProvenanceHttpApi;
import com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp;
import com.patra.registry.app.usecase.LiteratureProvenanceQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class LiteratureProvenanceApiImpl implements LiteratureProvenanceHttpApi {
    private final LiteratureProvenanceQueryUseCase queryUseCase;
    private final LiteratureProvenanceApiConverter apiConverter;

    @Override
    public LiteratureProvenanceConfigApiResp getConfigByCode(ProvenanceCode provenanceCode) {
        log.info("Received request to get config for provenance code: {}", provenanceCode);
        var view = queryUseCase.getConfigView(provenanceCode);
        return apiConverter.toConfigApiResp(view);
    }
}
