package dev.patra.analytics.infra.insight.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.enums.AlsoReadSource;
import dev.patra.analytics.domain.insight.model.vo.AlsoReadItem;
import dev.patra.catalog.api.publication.dto.PublicationLatestResp;
import dev.patra.catalog.api.publication.dto.PublicationResolutionResp;
import dev.patra.catalog.api.publication.dto.PublicationResolveByIdsReq;
import dev.patra.catalog.api.publication.dto.PublicationResolveResp;
import dev.patra.catalog.api.publication.dto.PublicationStateResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborReasonResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborsResp;
import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
import dev.patra.catalog.api.publication.endpoint.PublicationNeighborEndpoint;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class PublicationCatalogAdapterTest {

  private final PublicationEndpoint publications = mock(PublicationEndpoint.class);
  private final PublicationNeighborEndpoint neighbors = mock(PublicationNeighborEndpoint.class);
  private final PublicationCatalogAdapter adapter =
      new PublicationCatalogAdapter(publications, neighbors);

  @Test
  void eligible_requires_active_existing_and_not_retracted() {
    when(publications.resolveByIds(any()))
        .thenReturn(
            PublicationResolveResp.of(
                List.of(
                    row(1L, PublicationStateResp.ACTIVE, false),
                    row(2L, PublicationStateResp.DELETED, false),
                    row(3L, PublicationStateResp.ACTIVE, true),
                    PublicationResolutionResp.missing("4"))));

    assertThat(adapter.eligibleAmong(List.of(1L, 2L, 3L, 4L))).containsExactly(1L);
  }

  @Test
  void eligible_batches_by_100() {
    when(publications.resolveByIds(any())).thenReturn(PublicationResolveResp.of(List.of()));

    adapter.eligibleAmong(LongStream.rangeClosed(1, 250).boxed().toList());

    verify(publications, times(3)).resolveByIds(any(PublicationResolveByIdsReq.class));
    assertThat(adapter.eligibleAmong(List.of())).isEmpty();
  }

  @Test
  void latest_and_neighbors_are_mapped() {
    when(neighbors.latest(28)).thenReturn(PublicationLatestResp.of(List.of(9L, 8L)));
    when(neighbors.subjectNeighbors(5L, 11))
        .thenReturn(
            SubjectNeighborsResp.of(
                List.of(
                    SubjectNeighborResp.builder()
                        .id(1L)
                        .reason(SubjectNeighborReasonResp.SAME_MESH)
                        .meshDescriptorUi("D012772")
                        .meshTerm("Shock, Septic")
                        .build(),
                    SubjectNeighborResp.builder()
                        .id(2L)
                        .reason(SubjectNeighborReasonResp.SAME_VENUE)
                        .venueName("Chest")
                        .build())));

    assertThat(adapter.latestIds(28)).containsExactly(9L, 8L);
    assertThat(adapter.subjectNeighbors(5L, 11))
        .containsExactly(
            AlsoReadItem.of(1L, AlsoReadSource.SAME_MESH, "Shock, Septic", null),
            AlsoReadItem.of(2L, AlsoReadSource.SAME_VENUE, null, "Chest"));
  }

  @Test
  void remote_failure_becomes_unavailable() {
    when(publications.resolveByIds(any())).thenThrow(new ResourceAccessException("timeout"));
    when(neighbors.latest(28)).thenThrow(new IllegalStateException("no instance"));

    assertThatThrownBy(() -> adapter.eligibleAmong(List.of(1L)))
        .isInstanceOf(InsightUnavailableException.class);
    assertThatThrownBy(() -> adapter.latestIds(28)).isInstanceOf(InsightUnavailableException.class);
  }

  private static PublicationResolutionResp row(
      long id, PublicationStateResp state, boolean retracted) {
    return PublicationResolutionResp.builder()
        .key(Long.toString(id))
        .exists(true)
        .state(state)
        .id(id)
        .retracted(retracted)
        .build();
  }
}
