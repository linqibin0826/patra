package com.patra.registry.it;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("P4.3.3 — Crossref snapshot load")
class CrossrefSnapshotLoadTest extends BaseRegistryIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Returns seeds for fields/capabilities/renderRules/apiParamMappings")
  void crossrefSnapshotReturnsSeededData() throws Exception {
    mockMvc
        .perform(
            get("/_internal/expr/snapshot")
                .queryParam("provenanceCode", "CROSSREF")
                .queryParam("operationType", "ALL")
                .queryParam("endpointName", "SEARCH"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fields", not(empty())))
        .andExpect(jsonPath("$.fields[*].fieldKey", hasItem("text")))
        .andExpect(jsonPath("$.capabilities", not(empty())))
        .andExpect(jsonPath("$.renderRules", not(empty())))
        // Crossref param mappings: query->query and filter->filter (MULTI)
        .andExpect(
            jsonPath(
                "$.apiParamMappings[?(@.stdKey=='query' && @.providerParamName=='query')]",
                not(empty())))
        .andExpect(
            jsonPath(
                "$.apiParamMappings[?(@.stdKey=='filter' && @.providerParamName=='filter')]",
                not(empty())));
  }
}
