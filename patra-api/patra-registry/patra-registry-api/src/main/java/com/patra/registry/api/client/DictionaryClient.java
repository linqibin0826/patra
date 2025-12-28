package com.patra.registry.api.client;

import com.patra.registry.api.endpoint.DictionaryEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/// Registry 字典解析内部 API 的 Feign 客户端。
///
/// 扩展 {@link DictionaryEndpoint} 以通过 Spring Cloud OpenFeign 提供类型安全的 RPC 集成。
///
/// @author linqibin
/// @since 0.1.0
@FeignClient(name = "patra-registry", contextId = "dictionaryClient")
public interface DictionaryClient extends DictionaryEndpoint {}
