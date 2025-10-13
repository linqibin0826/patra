package com.patra.starter.provenance.common.converter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * XML to JSON converter.
 *
 * <p>Used only for APIs that lack native JSON support (e.g. PubMed EFetch). Converts the XML
 * payload to {@link JsonNode} first and then maps it to the target response type via a shared
 * {@link ObjectMapper}.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class XmlToJsonConverter {

  private static final int LOG_PAYLOAD_PREVIEW_LENGTH = 500;

  private final XmlMapper xmlMapper;
  private final ObjectMapper jsonMapper;

  /** Create a converter with tolerant XML and JSON mappers. */
  public XmlToJsonConverter() {
    this.xmlMapper = XmlMapper.builder().defaultUseWrapper(false).build();
    xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    xmlMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    this.jsonMapper = new ObjectMapper();
    jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * Convert XML string to typed JSON object.
   *
   * @param xml XML payload (mandatory)
   * @param responseClass target response type
   * @param <T> response type
   * @return mapped response instance
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
      log.error("[PROVENANCE][INTERNAL] Failed to convert XML to JSON: preview={}", preview, ex);
      throw new ProvenanceClientException(
          "UNKNOWN", "convert", null, null, preview, "Failed to convert XML to JSON", ex);
    }
  }
}
