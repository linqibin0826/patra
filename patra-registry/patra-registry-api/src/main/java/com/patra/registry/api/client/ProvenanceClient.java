package com.patra.registry.api.client;

import com.patra.registry.api.endpoint.ProvenanceEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/// Registry Provenance 内部 API 的 Feign 客户端。
/// 
/// 扩展 {@link ProvenanceEndpoint} 以通过 Spring Cloud OpenFeign 提供类型安全的 RPC 集成。
/// 
/// 使用示例:
/// 
/// ```java
/// @Autowired
/// private ProvenanceClient provenanceClient;
/// 
/// ProvenanceConfigResp config = provenanceClient.getConfiguration(
///     ProvenanceCode.PUBMED, "HARVEST", Instant.now());
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {}
