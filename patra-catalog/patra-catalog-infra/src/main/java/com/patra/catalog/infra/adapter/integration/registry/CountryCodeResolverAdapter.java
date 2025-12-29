package com.patra.catalog.infra.adapter.integration.registry;

import com.patra.catalog.domain.port.registry.CountryCodeResolverPort;
import com.patra.registry.api.client.DictionaryClient;
import com.patra.registry.api.dto.dict.DictionaryResolveItemResp;
import com.patra.registry.api.dto.dict.DictionaryResolveReq;
import com.patra.registry.api.dto.dict.DictionaryResolveResp;
import com.patra.starter.feign.error.exception.RemoteCallException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 国家编码解析适配器。
///
/// 通过调用 patra-registry 服务的字典解析 API 实现国家编码标准化。
///
/// **错误处理策略**：
///
/// - 远程服务不可用时返回空 Map，不影响主流程
/// - 国家编码是可选字段，解析失败不应阻断 venue enrich 流程
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class CountryCodeResolverAdapter implements CountryCodeResolverPort {

  private static final String DICTIONARY_TYPE_COUNTRY = "country";
  /// NLM LSIOU Country 字段使用 MARC Country Code Name List（英文国家全名）。
  ///
  /// @see <a href="https://www.nlm.nih.gov/bsd/licensee/catrecordxml_element_desc2.html">NLM
  // Catalog Record XML Elements</a>
  private static final String SOURCE_STANDARD_NAME_EN = "NAME_EN";
  private static final String STATUS_RESOLVED = "RESOLVED";

  private final DictionaryClient dictionaryClient;

  @Override
  public Map<String, String> resolveCountryCodes(Set<String> rawCodes) {
    if (rawCodes == null || rawCodes.isEmpty()) {
      return Map.of();
    }

    try {
      DictionaryResolveReq request =
          new DictionaryResolveReq(
              DICTIONARY_TYPE_COUNTRY, SOURCE_STANDARD_NAME_EN, List.copyOf(rawCodes));

      DictionaryResolveResp response = dictionaryClient.resolve(request);
      return extractResolvedCodes(response);

    } catch (RemoteCallException ex) {
      log.warn(
          "国家编码解析失败，registry 服务调用异常: httpStatus={}, errorCode={}, traceId={}",
          ex.getHttpStatus(),
          ex.getErrorCode(),
          ex.getTraceId());
      return Map.of();
    } catch (Exception ex) {
      log.warn("国家编码解析失败，发生意外异常", ex);
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
