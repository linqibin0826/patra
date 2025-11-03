package com.patra.storage.api.client;

import com.patra.storage.api.endpoint.StorageEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 存储元数据内部 API 的 Feign 客户端。
 *
 * <p>扩展 {@link StorageEndpoint} 以通过 Spring Cloud OpenFeign 提供类型安全的 RPC 集成。
 *
 * @author linqibin
 * @since 0.1.0
 */
@FeignClient(name = "patra-storage", contextId = "storageClient")
public interface StorageClient extends StorageEndpoint {}
