package com.patra.registry.api.rpc.contract;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public interface LiteratureProvenanceHttpApi {

    String BASE_PATH = "/_internal/literature-provenances";

    @GetMapping(BASE_PATH + "/{code}/config")
    LiteratureProvenanceConfigApiResp getConfigByCode(@PathVariable("code") ProvenanceCode provenanceCode);
}
