package com.patra.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Behavioral tests for {@link JsonMapperHolder}: lazy initialization and registration override. */
class JsonMapperHolderTest {

  private ObjectMapper previous;

  @AfterEach
  void tearDown() {
    if (previous != null) {
      JsonMapperHolder.register(previous);
    }
  }

  @Test
  void getObjectMapper_should_lazyInit_and_registerOverride() {
    // Capture the current instance as the baseline
    previous = JsonMapperHolder.getObjectMapper();
    ObjectMapper first = JsonMapperHolder.getObjectMapper();
    ObjectMapper second = JsonMapperHolder.getObjectMapper();
    assertThat(first).isSameAs(second);

    // Registering a new instance should override the cached mapper
    ObjectMapper custom = new ObjectMapper();
    JsonMapperHolder.register(custom);
    assertThat(JsonMapperHolder.getObjectMapper()).isSameAs(custom);
  }
}
