package com.patra.catalog.infra.adapter.integration.registry;

import com.patra.catalog.domain.model.enums.DictionaryType;
import com.patra.catalog.domain.model.vo.common.SourceStandard;
import com.patra.catalog.domain.port.registry.DictionaryResolverPort;
import dev.linqibin.commons.error.remote.RemoteCallException;
import dev.linqibin.patra.registry.api.dto.dict.DictionaryResolveItemResp;
import dev.linqibin.patra.registry.api.dto.dict.DictionaryResolveReq;
import dev.linqibin.patra.registry.api.dto.dict.DictionaryResolveResp;
import dev.linqibin.patra.registry.api.endpoint.DictionaryEndpoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 字典解析适配器。
///
/// 通过调用 patra-registry 服务的字典解析 API 实现字典值标准化。
///
/// **错误处理策略**：
///
/// - 远程服务不可用时返回空 Map，不影响主流程
/// - 字典解析通常是可选的增强操作，失败不应阻断业务流程
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class DictionaryResolverAdapter implements DictionaryResolverPort {

  private static final String STATUS_RESOLVED = "RESOLVED";

  private final DictionaryEndpoint dictionaryEndpoint;

  @Override
  public Map<String, String> resolve(
      DictionaryType dictionaryType, SourceStandard sourceStandard, Set<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) {
      return Map.of();
    }

    try {
      DictionaryResolveReq request =
          new DictionaryResolveReq(
              dictionaryType.getTypeCode(), sourceStandard.code(), List.copyOf(rawValues));

      DictionaryResolveResp response = dictionaryEndpoint.resolve(request);
      return extractResolvedCodes(response);

    } catch (RemoteCallException ex) {
      log.warn(
          "字典解析失败，registry 服务调用异常: type={}, standard={}, httpStatus={}, errorCode={}, traceId={}",
          dictionaryType.getTypeCode(),
          sourceStandard.code(),
          ex.getHttpStatus(),
          ex.getErrorCode(),
          ex.getTraceId());
      return Map.of();
    } catch (Exception ex) {
      log.warn(
          "字典解析失败，发生意外异常: type={}, standard={}",
          dictionaryType.getTypeCode(),
          sourceStandard.code(),
          ex);
      return Map.of();
    }
  }

  /// 从响应中提取成功解析的编码映射。
  private Map<String, String> extractResolvedCodes(DictionaryResolveResp response) {
    if (response == null || response.items() == null) {
      return Map.of();
    }

    Map<String, String> result = new HashMap<>();
    for (DictionaryResolveItemResp item : response.items()) {
      if (STATUS_RESOLVED.equals(item.status()) && item.resolvedCode() != null) {
        result.put(item.rawValue(), item.resolvedCode());
      }
    }
    return result;
  }
}
