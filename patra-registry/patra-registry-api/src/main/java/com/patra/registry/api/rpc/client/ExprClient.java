package com.patra.registry.api.rpc.client;

import com.patra.registry.api.rpc.endpoint.ExprEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Feign client definition for invoking registry expression internal API.
 *
 * <p>Extends {@link ExprEndpoint} to provide type-safe RPC integration via
 * Spring Cloud OpenFeign.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@FeignClient(name = "patra-registry", contextId = "exprClient")
public interface ExprClient extends ExprEndpoint {
}
