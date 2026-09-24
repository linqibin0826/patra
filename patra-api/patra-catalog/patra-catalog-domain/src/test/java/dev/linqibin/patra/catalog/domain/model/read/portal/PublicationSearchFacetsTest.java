package dev.linqibin.patra.catalog.domain.model.read.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PublicationSearchFacets 读模型")
class PublicationSearchFacetsTest {

  @Test
  @DisplayName("null 列表替换为空不可变列表，标量原样")
  void nullListsBecomeEmpty() {
    PublicationSearchFacets f =
        PublicationSearchFacets.builder().openAccess(0).total(18807).lastSyncedAt(null).build();
    assertThat(f.years()).isNotNull().isEmpty();
    assertThat(f.types()).isNotNull().isEmpty();
    assertThat(f.evidence()).isNotNull().isEmpty();
    assertThat(f.languages()).isNotNull().isEmpty();
    assertThat(f.total()).isEqualTo(18807);
    assertThat(f.lastSyncedAt()).isNull();
  }

  @Test
  @DisplayName("列表防御性拷贝")
  void copiesLists() {
    PublicationSearchFacets f =
        PublicationSearchFacets.builder()
            .years(List.of(FacetCount.of("2026", 1)))
            .lastSyncedAt(Instant.parse("2026-08-30T04:06:00Z"))
            .build();
    assertThat(f.years()).containsExactly(FacetCount.of("2026", 1));
    assertThat(f.lastSyncedAt()).isEqualTo(Instant.parse("2026-08-30T04:06:00Z"));
  }
}
