package dev.patra.catalog.adapter.portal.rest.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/// [PortalVenueListRequest] 紧凑构造器归一化行为测试。
class PortalVenueListRequestTest {

  @Test
  void givenPaddedAndBlankTokens_whenConstruct_thenTrimmedAndBlanksRemoved() {
    var req =
        new PortalVenueListRequest(
            null,
            null,
            Arrays.asList(" Medicine ", "", "  ", "Biology"),
            List.of(),
            List.of(),
            null,
            null,
            null,
            List.of(),
            null,
            null);
    assertThat(req.subject()).containsExactly("Medicine", "Biology");
  }

  @Test
  void givenNullLists_whenConstruct_thenEmptyImmutableLists() {
    var req =
        new PortalVenueListRequest(
            null, null, null, null, null, null, null, null, null, null, null);
    assertThat(req.subject()).isEmpty();
    assertThat(req.jcr()).isEmpty();
    assertThat(req.cas()).isEmpty();
    assertThat(req.country()).isEmpty();
  }

  @Test
  void givenOnlyBlankTokens_whenConstruct_thenEmptyList() {
    var req =
        new PortalVenueListRequest(
            null, null, Arrays.asList("", "  "), null, null, null, null, null, null, null, null);
    assertThat(req.subject()).isEmpty();
  }
}
