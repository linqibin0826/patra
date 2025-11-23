package com.patra.starter.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link BatchProperties} 单元测试
 *
 * @author Patra Team
 * @since 1.0.0
 */
class BatchPropertiesTest {

  @Test
  void defaultValues_ShouldBeCorrect() {
    // When
    BatchProperties properties = new BatchProperties();

    // Then
    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getTablePrefix()).isEqualTo("BATCH_");
    assertThat(properties.getObservability()).isNotNull();
    assertThat(properties.getChunk()).isNotNull();
  }

  @Test
  void observabilityProperties_ShouldHaveCorrectDefaults() {
    // When
    BatchProperties properties = new BatchProperties();
    BatchProperties.ObservabilityProperties observability = properties.getObservability();

    // Then
    assertThat(observability.getTracing()).isNotNull();
    assertThat(observability.getTracing().isEnabled()).isTrue();

    assertThat(observability.getMetrics()).isNotNull();
    assertThat(observability.getMetrics().isEnabled()).isTrue();

    assertThat(observability.getLogging()).isNotNull();
    assertThat(observability.getLogging().isEnabled()).isTrue();
    assertThat(observability.getLogging().getLevel()).isEqualTo("INFO");
  }

  @Test
  void chunkProperties_ShouldHaveCorrectDefaults() {
    // When
    BatchProperties properties = new BatchProperties();
    BatchProperties.ChunkProperties chunk = properties.getChunk();

    // Then
    assertThat(chunk.getDefaultSize()).isEqualTo(1000);
    assertThat(chunk.getMaxSize()).isEqualTo(10000);
  }

  @Test
  void setters_ShouldWorkCorrectly() {
    // Given
    BatchProperties properties = new BatchProperties();

    // When
    properties.setEnabled(false);
    properties.setTablePrefix("TEST_");
    properties.getChunk().setDefaultSize(500);
    properties.getChunk().setMaxSize(5000);

    // Then
    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getTablePrefix()).isEqualTo("TEST_");
    assertThat(properties.getChunk().getDefaultSize()).isEqualTo(500);
    assertThat(properties.getChunk().getMaxSize()).isEqualTo(5000);
  }
}
