package com.patra.registry.api.rpc.client;

import com.patra.registry.api.rpc.endpoint.ProvenanceEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        name = "patra-registry",
        contextId = "provenanceClient"
)
public interface ProvenanceClient extends ProvenanceEndpoint {
}
