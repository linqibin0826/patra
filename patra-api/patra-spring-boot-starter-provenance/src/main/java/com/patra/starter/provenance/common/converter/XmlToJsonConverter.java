package com.patra.starter.provenance.common.converter;

import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

/// XML 到 JSON 转换器
///
/// 仅用于缺乏原生 JSON 支持的 API(如 PubMed EFetch)。先将 XML 载荷转换为 {@link JsonNode}, 然后通过共享的 {@link
/// ObjectMapper} 映射到目标响应类型。
///
/// **转换流程:**
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class XmlToJsonConverter {

  private static final int LOG_PAYLOAD_PREVIEW_LENGTH = 500;

  private final XmlMapper xmlMapper;
  private final ObjectMapper jsonMapper;

  /// 创建带有宽容 XML 和 JSON 映射器的转换器
  ///
  /// 配置说明：
  ///
  /// - 禁用未知属性失败
  /// - 启用单值作为数组处理
  /// - XML 禁用默认包装器
  public XmlToJsonConverter() {
    this.xmlMapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build();

    this.jsonMapper =
        JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build();
  }

  /// 将 XML 字符串转换为类型化 JSON 对象
  ///
  /// @param xml XML 载荷(必填)
  /// @param responseClass 目标响应类型
  /// @param <T> 响应类型
  /// @return 映射后的响应实例
  public <T> T convert(String xml, Class<T> responseClass) {
    Objects.requireNonNull(responseClass, "responseClass cannot be null");
    if (xml == null || xml.isBlank()) {
      throw new ProvenanceClientException("UNKNOWN", "convert", "XML payload is empty");
    }

    try {
      JsonNode jsonNode = xmlMapper.readTree(xml);
      return jsonMapper.treeToValue(jsonNode, responseClass);
    } catch (Exception ex) {
      String preview = xml.substring(0, Math.min(LOG_PAYLOAD_PREVIEW_LENGTH, xml.length()));
      log.error("Failed to convert XML to JSON: preview={}", preview, ex);
      throw new ProvenanceClientException(
          "UNKNOWN", "convert", null, null, preview, "Failed to convert XML to JSON", ex);
    }
  }
}
