package dev.linqibin.patra.registry.adapter.rest.dictionary;

import dev.linqibin.patra.registry.adapter.rest.dictionary.mapper.DictionaryItemApiConverter;
import dev.linqibin.patra.registry.adapter.rest.dictionary.response.DictionaryItemListResponse;
import dev.linqibin.patra.registry.app.service.DictionaryQueryService;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryItemListResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// 字典项列表查询 Controller。
///
/// 提供面向前端消费的字典项列表查询 API，支持可选的本地化标签。
///
/// 端点:
///
/// - GET /dictionaries/items?typeCode={typeCode}&labelStandard={labelStandard}
///
/// 设计说明:
///
/// 独立于 {@code DictionaryEndpointImpl}，因为此 API 仅供前端消费，
/// 不涉及跨服务 RPC 契约，无需放入 API 模块。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RestController
@RequestMapping("/dictionaries")
@RequiredArgsConstructor
public class DictionaryItemController {

  private final DictionaryQueryService queryService;
  private final DictionaryItemApiConverter converter;

  /// 查询指定字典类型下所有启用的字典项。
  ///
  /// @param typeCode 字典类型代码（必填，如 "country"、"language"）
  /// @param labelStandard 本地化标签标准代码（可选，如 "NAME_ZH"）
  /// @return 字典项列表响应
  @GetMapping("/items")
  public DictionaryItemListResponse listItems(
      @RequestParam String typeCode, @RequestParam(required = false) String labelStandard) {
    log.debug(
        "Received dictionary list items request: typeCode [{}], labelStandard [{}]",
        typeCode,
        labelStandard);

    DictionaryItemListResult result = queryService.listItems(typeCode, labelStandard);
    return converter.toResponse(result);
  }
}
