package com.patra.registry.api.client;

import com.patra.registry.api.endpoint.ExprEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/// Registry Expression 内部 API 的 Feign 客户端。
///
/// 扩展 {@link ExprEndpoint} 以通过 Spring Cloud OpenFeign 提供类型安全的 RPC 集成。
///
/// 使用示例:
///
/// ```java
/// @Autowired
/// private ExprClient exprClient;
///
/// ExprSnapshotResp snapshot = exprClient.getSnapshot(
///     "PUBMED", "HARVEST", null, Instant.now());
/// ```
///
/// @author linqibin
/// @since 0.1.0
@FeignClient(name = "patra-registry", contextId = "exprClient")
public interface ExprClient extends ExprEndpoint {}
