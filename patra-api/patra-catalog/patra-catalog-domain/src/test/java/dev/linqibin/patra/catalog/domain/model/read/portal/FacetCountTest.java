package dev.linqibin.patra.catalog.domain.model.read.portal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FacetCount 顶层 facet 项")
class FacetCountTest {

  @Test
  @DisplayName("of() 持有维度值与计数")
  void ofHoldsValueAndCount() {
    FacetCount fc = FacetCount.of("2026", 17520L);
    assertThat(fc.value()).isEqualTo("2026");
    assertThat(fc.count()).isEqualTo(17520L);
  }
}
