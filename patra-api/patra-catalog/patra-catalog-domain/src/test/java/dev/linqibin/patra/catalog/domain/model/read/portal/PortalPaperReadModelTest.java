package dev.linqibin.patra.catalog.domain.model.read.portal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PortalPaperReadModel 紧凑构造器")
class PortalPaperReadModelTest {

  @Test
  @DisplayName("authors null → 空列表，evidenceLevel null → UNKNOWN")
  void defaults() {
    PortalPaperReadModel m = PortalPaperReadModel.builder().id(1L).title("t").build();
    assertThat(m.authors()).isNotNull().isEmpty();
    assertThat(m.evidenceLevel()).isEqualTo(EvidenceLevel.UNKNOWN);
    assertThat(m.venueId()).isNull();
    assertThat(m.abstractSnippet()).isNull();
  }

  @Test
  @DisplayName("新三字段透传")
  void carriesNewFields() {
    PortalPaperReadModel m =
        PortalPaperReadModel.builder()
            .id(1L)
            .title("t")
            .venueId(42L)
            .evidenceLevel(EvidenceLevel.SYSTEMATIC_REVIEW)
            .abstractSnippet("snip")
            .authors(List.of("A"))
            .build();
    assertThat(m.venueId()).isEqualTo(42L);
    assertThat(m.evidenceLevel()).isEqualTo(EvidenceLevel.SYSTEMATIC_REVIEW);
    assertThat(m.abstractSnippet()).isEqualTo("snip");
  }
}
