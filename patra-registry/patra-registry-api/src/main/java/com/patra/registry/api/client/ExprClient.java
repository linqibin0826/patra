package com.patra.registry.api.client;

import com.patra.registry.api.endpoint.ExprEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Feign client for registry expression internal API.
 *
 * <p>Extends {@link ExprEndpoint} to provide type-safe RPC integration via Spring Cloud OpenFeign.
 *
 * @author linqibin
 * @since 0.1.0
 */
@FeignClient(name = "patra-registry", contextId = "exprClient")
public interface ExprClient extends ExprEndpoint {}
