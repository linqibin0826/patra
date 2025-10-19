package com.patra.ingest.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlanMetadataTest {

  @Test
  void shouldCreateValidMetadata() {
    PlanMetadata metadata = new PlanMetadata(100, "env123", "key456");

    assertThat(metadata.totalCount()).isEqualTo(100);
    assertThat(metadata.webEnv()).isEqualTo("env123");
    assertThat(metadata.queryKey()).isEqualTo("key456");
    assertThat(metadata.hasWebEnv()).isTrue();
  }

  @Test
  void shouldCreateEmptyMetadata() {
    PlanMetadata metadata = PlanMetadata.empty();

    assertThat(metadata.totalCount()).isZero();
    assertThat(metadata.webEnv()).isNull();
    assertThat(metadata.queryKey()).isNull();
    assertThat(metadata.hasWebEnv()).isFalse();
  }

  @Test
  void shouldRejectNegativeCount() {
    assertThatThrownBy(() -> new PlanMetadata(-1, "env", "key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("totalCount must be >= 0");
  }

  @Test
  void shouldRejectWebEnvWithoutQueryKey() {
    assertThatThrownBy(() -> new PlanMetadata(10, "env123", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("webEnv and queryKey must either both be present or both absent");
  }

  @Test
  void shouldRejectQueryKeyWithoutWebEnv() {
    assertThatThrownBy(() -> new PlanMetadata(10, null, "key456"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("webEnv and queryKey must either both be present or both absent");
  }

  @Test
  void shouldAcceptBothNullOrBothPresent() {
    PlanMetadata none = new PlanMetadata(10, null, null);
    assertThat(none.hasWebEnv()).isFalse();

    PlanMetadata both = new PlanMetadata(10, "env", "key");
    assertThat(both.hasWebEnv()).isTrue();
  }
}
