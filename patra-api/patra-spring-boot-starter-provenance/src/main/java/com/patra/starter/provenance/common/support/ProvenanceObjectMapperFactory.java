package com.patra.starter.provenance.common.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Shared factory for provenance {@link ObjectMapper} instances.
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class ProvenanceObjectMapperFactory {

  private ProvenanceObjectMapperFactory() {}

  /**
   * Build an {@link ObjectMapper} that tolerates lenient upstream payloads.
   *
   * @return shared mapper instance with relaxed deserialization settings
   */
  public static ObjectMapper createJsonMapper() {
    return JsonMapper.builder()
        .findAndAddModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        .build();
  }
}
