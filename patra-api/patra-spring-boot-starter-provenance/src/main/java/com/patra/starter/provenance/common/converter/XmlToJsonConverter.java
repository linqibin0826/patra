package com.patra.starter.provenance.common.converter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import lombok.extern.slf4j.Slf4j;

/**
 * XML to JSON converter.
 * Only used when API does not support JSON format natively.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class XmlToJsonConverter {

    private final XmlMapper xmlMapper;
    private final ObjectMapper jsonMapper;

    public XmlToJsonConverter() {
        // Configure XmlMapper to support complex XML structures
        this.xmlMapper = XmlMapper.builder()
            .defaultUseWrapper(false)  // Don't automatically wrap root element
            .build();
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        xmlMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        this.jsonMapper = new ObjectMapper();
    }

    /**
     * Convert XML string to JSON object.
     * Only used when API does not support JSON format natively.
     *
     * @param xml           XML string
     * @param responseClass response type
     * @param <T>          response type
     * @return response object
     * @throws ProvenanceClientException if conversion fails
     */
    public <T> T convert(String xml, Class<T> responseClass) {
        try {
            // 1. Parse XML to JsonNode
            JsonNode jsonNode = xmlMapper.readTree(xml);

            // 2. Convert to target type
            return jsonMapper.treeToValue(jsonNode, responseClass);
        } catch (Exception e) {
            log.error("[PROVENANCE][INTERNAL] Failed to convert XML to JSON: xml={}",
                xml.substring(0, Math.min(500, xml.length())), e);
            throw new ProvenanceClientException(
                "UNKNOWN", "convert", "Failed to convert XML to JSON", e
            );
        }
    }
}
