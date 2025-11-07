package com.patra.starter.provenance.common.converter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * XML 到 JSON 转换器
 *
 * <p>仅用于缺乏原生 JSON 支持的 API(如 PubMed EFetch)。先将 XML 载荷转换为 {@link JsonNode}, 然后通过共享的 {@link
 * ObjectMapper} 映射到目标响应类型。
 *
 * <p><b>转换流程:</b>
 *
 * <ol>
 *   <li>使用宽容的 {@link XmlMapper} 解析 XML 字符串为 JsonNode
 *   <li>配置忽略未知属性和接受单值作为数组
 *   <li>使用标准 {@link ObjectMapper} 将 JsonNode 转换为目标类型
 *   <li>失败时记录前500字符的载荷预览用于排查
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class XmlToJsonConverter {

  private static final int LOG_PAYLOAD_PREVIEW_LENGTH = 500;

  private final XmlMapper xmlMapper;
  private final ObjectMapper jsonMapper;

  /** 创建带有宽容 XML 和 JSON 映射器的转换器 */
  public XmlToJsonConverter() {
    this.xmlMapper = XmlMapper.builder().defaultUseWrapper(false).build();
    xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    xmlMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    this.jsonMapper = new ObjectMapper();
    jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    jsonMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
  }

  /**
   * 将 XML 字符串转换为类型化 JSON 对象
   *
   * @param xml XML 载荷(必填)
   * @param responseClass 目标响应类型
   * @param <T> 响应类型
   * @return 映射后的响应实例
   */
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
