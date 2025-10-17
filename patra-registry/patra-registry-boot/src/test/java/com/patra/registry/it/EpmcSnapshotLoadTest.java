package com.patra.registry.it;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("P4.3.2 — EPMC snapshot load")
class EpmcSnapshotLoadTest extends BaseRegistryIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Returns seeds for fields/capabilities/renderRules/apiParamMappings")
  void epmcSnapshotReturnsSeededData() throws Exception {
    mockMvc
        .perform(
            get("/_internal/expr/snapshot")
                .queryParam("provenanceCode", "EPMC")
                .queryParam("operationType", "ALL")
                .queryParam("endpointName", "SEARCH"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fields", not(empty())))
        .andExpect(jsonPath("$.fields[*].fieldKey", hasItems("publication_date", "text")))
        .andExpect(jsonPath("$.capabilities", not(empty())))
        .andExpect(jsonPath("$.renderRules", not(empty())))
        // EPMC param mappings: query->query and limit->pageSize
        .andExpect(
            jsonPath(
                "$.apiParamMappings[?(@.stdKey=='query' && @.providerParamName=='query')]",
                not(empty())))
        .andExpect(
            jsonPath(
                "$.apiParamMappings[?(@.stdKey=='limit' && @.providerParamName=='pageSize')]",
                not(empty())));
  }
}
