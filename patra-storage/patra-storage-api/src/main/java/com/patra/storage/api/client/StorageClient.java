package com.patra.storage.api.client;

import com.patra.storage.api.endpoint.StorageEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Feign client for storage metadata internal API.
 *
 * <p>Extends {@link StorageEndpoint} to provide type-safe RPC integration via Spring Cloud
 * OpenFeign.
 *
 * @author linqibin
 * @since 0.1.0
 */
@FeignClient(name = "patra-storage", contextId = "storageClient")
public interface StorageClient extends StorageEndpoint {}
