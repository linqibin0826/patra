package com.patra.starter.provenance.pubmed.model.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EFetchRequestTest {

  @Test
  @DisplayName("toQueryParams should include ID list when provided")
  void shouldBuildQueryParamsForIdList() {
    EFetchRequest request = new EFetchRequest("pubmed", "1,2,3");

    Map<String, String> params = request.toQueryParams();

    assertEquals("pubmed", params.get("db"));
    assertEquals("1,2,3", params.get("id"));
    assertEquals("xml", params.get("retmode"));
    assertEquals("abstract", params.get("rettype"));
  }

  @Test
  @DisplayName("History server requests should omit id when WebEnv and QueryKey are present")
  void shouldAllowHistoryServerRequestWithoutId() {
    EFetchRequest request =
        new EFetchRequest(
            "pubmed", null, null, null, 0, 500, "MCID_history", "1", null, null, null);

    Map<String, String> params = request.toQueryParams();

    assertEquals("pubmed", params.get("db"));
    assertFalse(params.containsKey("id"));
    assertEquals("MCID_history", params.get("WebEnv"));
    assertEquals("1", params.get("query_key"));
    assertEquals("xml", params.get("retmode"));
    assertEquals("abstract", params.get("rettype"));
  }

  @Test
  @DisplayName("Constructor should reject requests without identifiers or history context")
  void shouldRejectWhenIdentifiersMissing() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new EFetchRequest(
                    "pubmed", "   ", null, null, null, null, null, null, null, null, null));

    assertTrue(ex.getMessage().contains("Either id or (WebEnv + queryKey) must be provided"));
  }
}
