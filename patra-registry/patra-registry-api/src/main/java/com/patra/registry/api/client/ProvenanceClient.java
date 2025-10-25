package com.patra.registry.api.client;

import com.patra.registry.api.endpoint.ProvenanceEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Feign client for registry provenance internal API.
 *
 * <p>Extends {@link ProvenanceEndpoint} to provide type-safe RPC integration via Spring Cloud
 * OpenFeign.
 *
 * @author linqibin
 * @since 0.1.0
 */
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {}
