package dev.patra.tool.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BatchPropertiesTest {

  @Test
  void datasource_is_configured_only_with_a_non_blank_url() {
    BatchProperties.Datasource.Hikari hikari =
        new BatchProperties.Datasource.Hikari(5, 2, 30000L, 600000L, 1800000L);
    assertThat(new BatchProperties.Datasource(null, null, null, null, hikari).configured())
        .isFalse();
    assertThat(new BatchProperties.Datasource("  ", null, null, null, hikari).configured())
        .isFalse();
    assertThat(
            new BatchProperties.Datasource("jdbc:postgresql://x/y", null, null, null, hikari)
                .configured())
        .isTrue();
  }

  @Test
  void import_limit_only_counts_positive_values() {
    assertThat(new BatchProperties.ImportLimit(-1).hasLimit()).isFalse();
    assertThat(new BatchProperties.ImportLimit(0).hasLimit()).isFalse();
    assertThat(new BatchProperties.ImportLimit(10).hasLimit()).isTrue();
  }
}
