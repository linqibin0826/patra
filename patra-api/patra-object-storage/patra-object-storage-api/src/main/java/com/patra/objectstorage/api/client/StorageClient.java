package com.patra.objectstorage.api.client;

import com.patra.objectstorage.api.endpoint.StorageEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/// 存储元数据内部 API 的 Feign 客户端。
///
/// 扩展 {@link StorageEndpoint} 以通过 Spring Cloud OpenFeign 提供类型安全的 RPC 集成。
///
/// @author linqibin
/// @since 0.1.0
@FeignClient(name = "patra-object-storage", contextId = "storageClient")
public interface StorageClient extends StorageEndpoint {}
