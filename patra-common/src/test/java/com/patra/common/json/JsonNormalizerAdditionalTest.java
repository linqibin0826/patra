package com.patra.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JsonNormalizerAdditionalTest {

  private static final class PayloadHolder {
    private final String name;
    private final byte[] payload;
    private final Instant createdAt;

    private PayloadHolder(String name, byte[] payload, Instant createdAt) {
      this.name = name;
      this.payload = payload;
      this.createdAt = createdAt;
    }

    public String getName() {
      return name;
    }

    public byte[] getPayload() {
      return payload;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }
  }

  @Test
  void normalize_shouldHandlePojoNodeWithBinary() {
    PayloadHolder holder =
        new PayloadHolder("sample", new byte[] {1, 2, 3}, Instant.parse("2024-05-01T12:30:15Z"));
    JsonNormalizer normalizer = JsonNormalizer.usingDefault();

    JsonNormalizerResult result = normalizer.normalize(JsonNodeFactory.instance.pojoNode(holder));

    assertThat(result.getCanonicalJson()).contains("\"payload\":\"AQID\"");
    assertThat(result.getCanonicalJson()).contains("\"createdAt\":1714566615");
    assertThat(result.getCanonicalJson()).contains("\"name\":\"sample\"");
  }

  @Test
  void normalize_binaryNode_shouldDropEmptyPayload() {
    JsonNormalizer normalizer = JsonNormalizer.usingDefault();

    JsonNormalizerResult result =
        normalizer.normalize(JsonNodeFactory.instance.binaryNode(new byte[0]));

    assertThat(result.getCanonicalValue()).isNull();
    assertThat(result.getCanonicalJson()).isEqualTo("null");
  }
}
