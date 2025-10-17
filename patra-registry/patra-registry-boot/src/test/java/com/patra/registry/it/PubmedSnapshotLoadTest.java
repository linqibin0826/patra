package com.patra.registry.it;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("P4.3.1 — PubMed snapshot load")
class PubmedSnapshotLoadTest extends BaseRegistryIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Returns fields/capabilities/renderRules/apiParamMappings as seeded")
  void pubmedSnapshotReturnsSeededData() throws Exception {
    mockMvc
        .perform(
            get("/_internal/expr/snapshot")
                .queryParam("provenanceCode", "PUBMED")
                .queryParam("operationType", "ALL")
                .queryParam("endpointName", "SEARCH"))
        .andExpect(status().isOk())
        // fields exist and include entrez_date and tiab
        .andExpect(jsonPath("$.fields", not(empty())))
        .andExpect(jsonPath("$.fields[*].fieldKey", hasItems("entrez_date", "tiab")))
        // capabilities present
        .andExpect(jsonPath("$.capabilities", not(empty())))
        // render rules present
        .andExpect(jsonPath("$.renderRules", not(empty())))
        // apiParamMappings include query->term and to->maxdate (with transform)
        .andExpect(jsonPath("$.apiParamMappings", not(empty())))
        .andExpect(
            jsonPath(
                "$.apiParamMappings[?(@.stdKey=='query' && @.providerParamName=='term')]",
                not(empty())))
        .andExpect(
            jsonPath(
                "$.apiParamMappings[?(@.stdKey=='to' && @.providerParamName=='maxdate' && @.transformCode=='TO_EXCLUSIVE_MINUS_1D')]",
                not(empty())));
  }
}
