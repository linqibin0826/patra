package com.patra.registry.adapter.rest;

import com.patra.registry.adapter.rest.converter.DictionaryApiConverter;
import com.patra.registry.api.dto.dict.DictionaryResolveReq;
import com.patra.registry.api.dto.dict.DictionaryResolveResp;
import com.patra.registry.api.endpoint.DictionaryEndpoint;
import com.patra.registry.app.service.DictionaryQueryService;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/// 字典解析 REST 控制器,提供字典解析 HTTP API。
///
/// 端点:
///
/// - POST /_internal/dictionaries/resolve - 批量解析字典值
///
/// 职责:
///
/// - 接收解析请求并调用 {@link DictionaryQueryService}
/// - 通过 {@link DictionaryApiConverter} 转换为 API 响应 DTO
///
/// 权限: 内部服务间调用(/_internal 路径)
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RestController
@RequiredArgsConstructor
public class DictionaryEndpointImpl implements DictionaryEndpoint {

  private final DictionaryQueryService queryService;
  private final DictionaryApiConverter converter;

  /// 批量解析字典值。
  ///
  /// @param request 批量解析请求
  /// @return 批量解析结果
  @Override
  public DictionaryResolveResp resolve(DictionaryResolveReq request) {
    log.debug(
        "Received dictionary resolve request: typeCode [{}], sourceStandard [{}], size [{}]",
        request.typeCode(),
        request.sourceStandard(),
        request.rawValues().size());

    DictionaryResolveQuery result =
        queryService.resolveBatch(
            request.typeCode(), request.sourceStandard(), request.rawValues());

    return converter.toResp(result);
  }
}
