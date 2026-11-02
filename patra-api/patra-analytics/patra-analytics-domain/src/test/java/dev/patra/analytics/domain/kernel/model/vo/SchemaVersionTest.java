package dev.patra.analytics.domain.kernel.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SchemaVersionTest {

  @Test
  void parse_accepts_model_one_versions() {
    assertThat(SchemaVersion.parse("1-0-3"))
        .hasValueSatisfying(
            version -> {
              assertThat(version.addition()).isEqualTo(3);
              assertThat(version.accepted()).isTrue();
            });
  }

  @Test
  void parse_rejects_model_two() {
    assertThat(SchemaVersion.parse("2-0-0"))
        .hasValueSatisfying(version -> assertThat(version.accepted()).isFalse());
  }

  @Test
  void parse_returns_empty_for_garbage() {
    assertThat(SchemaVersion.parse("1.0.0")).isEmpty();
    assertThat(SchemaVersion.parse("")).isEmpty();
    assertThat(SchemaVersion.parse("1-0-0\n")).isEmpty();
    assertThat(SchemaVersion.parse(null)).isEmpty();
  }
}
