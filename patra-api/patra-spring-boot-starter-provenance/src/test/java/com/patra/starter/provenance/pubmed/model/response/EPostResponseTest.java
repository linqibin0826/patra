package com.patra.starter.provenance.pubmed.model.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EPostResponseTest {

  private static final XmlMapper XML_MAPPER = createXmlMapper();

  private static XmlMapper createXmlMapper() {
    return XmlMapper.builder()
        .findAndAddModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .defaultUseWrapper(false)
        .build();
  }

  @Test
  @DisplayName("XmlMapper should parse standard EPost response")
  void shouldParseStandardPayload() throws Exception {
    String payload =
        """
        <ePostResult>
          <QueryKey>1</QueryKey>
          <WebEnv>MCID_example</WebEnv>
          <Count>500</Count>
        </ePostResult>
        """;

    EPostResponse response = XML_MAPPER.readValue(payload, EPostResponse.class);

    assertEquals("MCID_example", response.webEnv());
    assertEquals("1", response.queryKey());
    assertEquals(500, response.count());
    assertTrue(response.isValid());
  }

  @Test
  @DisplayName("XmlMapper should tolerate lower-case element names")
  void shouldSupportLowerCaseElements() throws Exception {
    String payload =
        """
        <ePostResult>
          <querykey>2</querykey>
          <webenv>MCID_lower</webenv>
        </ePostResult>
        """;

    EPostResponse response = XML_MAPPER.readValue(payload, EPostResponse.class);

    assertEquals("MCID_lower", response.webEnv());
    assertEquals("2", response.queryKey());
    assertTrue(response.isValid());
  }

  @Test
  @DisplayName("Response without QueryKey should be marked invalid")
  void shouldIdentifyInvalidResponse() throws Exception {
    String payload =
        """
        <ePostResult>
          <WebEnv>only-webenv</WebEnv>
        </ePostResult>
        """;

    EPostResponse response = XML_MAPPER.readValue(payload, EPostResponse.class);

    assertFalse(response.isValid());
  }
}
