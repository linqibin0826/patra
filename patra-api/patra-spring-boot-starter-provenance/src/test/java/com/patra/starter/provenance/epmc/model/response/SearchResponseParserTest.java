package com.patra.starter.provenance.epmc.model.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResponseParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldParseResultsAndRequest() throws Exception {
        String payload = """
            {
              \"version\": \"6.9\",
              \"hitCount\": 10,
              \"nextCursorMark\": \"cursor-1\",
              \"nextPageUrl\": \"https://example.com\",
              \"request\": {
                \"queryString\": \"cancer\",
                \"resultType\": \"lite\",
                \"cursorMark\": \"*\",
                \"pageSize\": 2,
                \"sort\": \"\",
                \"synonym\": false
              },
              \"resultList\": {
                \"result\": [
                  {
                    \"id\": \"41043451\",
                    \"source\": \"MED\",
                    \"pmid\": \"41043451\",
                    \"doi\": \"10.1016/j.lanplh.2025.101332\",
                    \"title\": \"Interpreting substitution models\",
                    \"authorString\": \"Kliemann N\",
                    \"journalTitle\": \"Lancet Planet Health\",
                    \"pubYear\": \"2026\",
                    \"abstractText\": \"Sample abstract\",
                    \"citedByCount\": 0
                  }
                ]
              }
            }
            """;

        SearchResponse response = SearchResponse.from(MAPPER.readTree(payload));
        assertThat(response.hitCount()).isEqualTo(10);
        assertThat(response.version()).isEqualTo("6.9");
        assertThat(response.request().queryString()).isEqualTo("cancer");
        assertThat(response.request().pageSize()).isEqualTo(2);
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo("41043451");
            assertThat(result.doi()).isEqualTo("10.1016/j.lanplh.2025.101332");
            assertThat(result.abstractText()).isEqualTo("Sample abstract");
        });
    }
}
