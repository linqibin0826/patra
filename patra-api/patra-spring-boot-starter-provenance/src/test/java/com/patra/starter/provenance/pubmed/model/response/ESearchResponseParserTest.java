package com.patra.starter.provenance.pubmed.model.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ESearchResponseParserTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void shouldParseCoreFieldsAndLists() throws Exception {
    String payload =
        """
            {
              \"header\": {\"type\": \"esearch\", \"version\": \"0.3\"},
              \"esearchresult\": {
                \"count\": \"2\",
                \"retmax\": \"2\",
                \"retstart\": \"0\",
                \"idlist\": [\"123\", \"456\"],
                \"translationset\": [{\"from\": \"cancer\", \"to\": \"cancer\"}],
                \"translationstack\": [{\"term\": \"cancer\"}],
                \"webenv\": \"test-env\",
                \"querykey\": \"1\",
                \"querytranslation\": \"cancer\",
                \"errorlist\": {\"phrase\": [\"bad term\"]},
                \"warnings\": {\"outputmessage\": [\"warn\"]}
              }
            }
            """;

    ESearchResponse response = MAPPER.readValue(payload, ESearchResponse.class);

    assertThat(response.result().count()).isEqualTo(2);
    assertThat(response.result().idList()).containsExactly("123", "456");
    assertThat(response.result().translationSet()).hasSize(1);
    assertThat(response.result().translationStack()).hasSize(1);
  }
}
