package com.patra.registry.api.rpc.client;

import com.patra.registry.api.rpc.contract.LiteratureProvenanceHttpApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        name = "patra-registry",
        contextId = "literatureProvenanceConfigClient"
)
public interface LiteratureProvenanceClient extends LiteratureProvenanceHttpApi {


}
