package com.patra.registry.api.endpoint;

import com.patra.registry.api.dto.dict.DictionaryResolveReq;
import com.patra.registry.api.dto.dict.DictionaryResolveResp;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/// 字典解析内部 API 契约接口。
///
/// 通过 Feign 客户端向内部微服务暴露字典批量解析能力。
///
/// 端点:
///
/// - POST /_internal/dictionaries/resolve - 批量解析字典值
///
/// @author linqibin
/// @since 0.1.0
public interface DictionaryEndpoint {

  String BASE_PATH = "/_internal/dictionaries";

  /// 批量解析字典值。
  ///
  /// @param request 批量解析请求
  /// @return 批量解析结果
  @PostMapping(BASE_PATH + "/resolve")
  DictionaryResolveResp resolve(@RequestBody DictionaryResolveReq request);
}
