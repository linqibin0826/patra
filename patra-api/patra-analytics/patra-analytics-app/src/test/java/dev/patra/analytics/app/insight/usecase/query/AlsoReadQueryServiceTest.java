package dev.patra.analytics.app.insight.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.enums.AlsoReadSource;
import dev.patra.analytics.domain.insight.model.vo.AlsoReadItem;
import dev.patra.analytics.domain.insight.port.catalog.PublicationCatalogPort;
import dev.patra.analytics.domain.insight.port.read.CoReadingReadPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlsoReadQueryServiceTest {

  private final CoReadingReadPort coReads = mock(CoReadingReadPort.class);
  private final PublicationCatalogPort catalog = mock(PublicationCatalogPort.class);
  private final AlsoReadQueryService service = new AlsoReadQueryService(coReads, catalog);

  @Test
  void six_co_reads_do_not_call_catalog() {
    when(coReads.relatedOf(9L, 6)).thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L));

    assertThat(service.alsoRead(9L)).hasSize(6).allMatch(i -> i.source() == AlsoReadSource.CO_READ);
    verify(catalog, never()).subjectNeighbors(anyLong(), anyInt());
  }

  @Test
  void backfills_from_neighbors() {
    when(coReads.relatedOf(9L, 6)).thenReturn(List.of(1L));
    when(catalog.subjectNeighbors(9L, 11))
        .thenReturn(
            List.of(
                AlsoReadItem.of(1L, AlsoReadSource.SAME_MESH, "Sepsis", null),
                AlsoReadItem.of(2L, AlsoReadSource.SAME_VENUE, null, "Chest")));

    assertThat(service.alsoRead(9L))
        .containsExactly(
            AlsoReadItem.coRead(1L), AlsoReadItem.of(2L, AlsoReadSource.SAME_VENUE, null, "Chest"));
  }

  @Test
  void catalog_unavailable_returns_co_read_only() {
    when(coReads.relatedOf(9L, 6)).thenReturn(List.of(1L, 2L));
    when(catalog.subjectNeighbors(9L, 11)).thenThrow(new InsightUnavailableException("down", null));

    assertThat(service.alsoRead(9L))
        .containsExactly(AlsoReadItem.coRead(1L), AlsoReadItem.coRead(2L));
  }
}
