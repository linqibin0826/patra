package com.patra.registry.api.rpc.client;

import com.patra.registry.api.rpc.endpoint.ExprEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "patra-registry", contextId = "exprClient")
public interface ExprClient extends ExprEndpoint {
}
