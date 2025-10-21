package com.patra.starter.provenance.common.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.starter.provenance.common.config.HttpConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.config.RateLimitConfig;
import com.patra.starter.provenance.common.config.RetryConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GatewayRequestBuilderTest {

  private final GatewayRequestBuilder builder = new GatewayRequestBuilder();

  @Test
  void buildShouldEncodeQueryHeadersAndResilience() {
    ApiRequest request =
        () ->
            Map.of(
                "db", "pubmed",
                "term", "cancer therapy");
    ProvenanceConfig config =
        new ProvenanceConfig(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/",
            new HttpConfig(Map.of("User-Agent", "Papertrace/0.1.0"), 1500, 2000, 2500),
            null,
            null,
            null,
            new RetryConfig(2, 500),
            new RateLimitConfig(null, 5));

    ExternalCallRequestDTO dto = builder.build(config.baseUrl(), "/esearch.fcgi", request, config);

    assertThat(dto.url())
        .startsWith("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?")
        .contains("db=pubmed")
        .contains("term=cancer+therapy");
    assertThat(dto.method()).isEqualTo("GET");
    assertThat(dto.headers()).containsEntry("User-Agent", "Papertrace/0.1.0");
    assertThat(dto.config().timeoutSeconds()).isEqualTo(2L);
    assertThat(dto.config().maxRetries()).isEqualTo(2);
    assertThat(dto.config().retryBackoffSeconds()).isEqualTo(1L);
    assertThat(dto.config().rateLimit()).isEqualTo(5);
  }

  @Test
  void buildShouldUseGetWhenUrlIsShort() {
    // Simulate small ID list (< 200 IDs)
    String smallIdList = "12345678,23456789,34567890"; // 3 IDs, ~26 chars
    ApiRequest request = () -> Map.of("db", "pubmed", "id", smallIdList);
    ProvenanceConfig config =
        new ProvenanceConfig(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/", null, null, null, null, null, null);

    ExternalCallRequestDTO dto = builder.build(config.baseUrl(), "/efetch.fcgi", request, config);

    assertThat(dto.method()).isEqualTo("GET");
    assertThat(dto.url()).contains("id=12345678%2C23456789"); // Commas are URL-encoded
    assertThat(dto.body()).isNull();
  }

  @Test
  void buildShouldUsePOSTWhenUrlIsTooLong() {
    // Simulate large ID list (> 200 IDs, causing URL > 1800 chars)
    String largeIdList = generatePmidList(300); // 300 IDs × ~9 chars = ~2700 chars
    ApiRequest request = () -> Map.of("db", "pubmed", "id", largeIdList, "retmode", "xml");
    ProvenanceConfig config =
        new ProvenanceConfig(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/", null, null, null, null, null, null);

    ExternalCallRequestDTO dto = builder.build(config.baseUrl(), "/efetch.fcgi", request, config);

    assertThat(dto.method()).isEqualTo("POST");
    assertThat(dto.url()).isEqualTo("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi");
    // Commas are URL-encoded as %2C in form data
    assertThat(dto.body()).contains("id=10000001%2C10000002");
    assertThat(dto.body()).contains("db=pubmed");
    assertThat(dto.body()).contains("retmode=xml");
    assertThat(dto.headers()).containsEntry("Content-Type", "application/x-www-form-urlencoded");
  }

  @Test
  void buildShouldUsePostWhenExactlyAtThreshold() {
    // Create ID list that makes URL exactly 1801 chars (just over threshold)
    int targetLength = 1801;
    String baseUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?";
    int overhead = baseUrl.length() + "db=pubmed&id=".length();
    int idListLength = targetLength - overhead;

    // Generate ID list to reach target length
    StringBuilder idList = new StringBuilder();
    while (idList.length() < idListLength) {
      if (!idList.isEmpty()) idList.append(",");
      idList.append("12345678");
    }

    ApiRequest request = () -> Map.of("db", "pubmed", "id", idList.toString());
    ProvenanceConfig config =
        new ProvenanceConfig(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/", null, null, null, null, null, null);

    ExternalCallRequestDTO dto = builder.build(config.baseUrl(), "/efetch.fcgi", request, config);

    assertThat(dto.method()).isEqualTo("POST");
    assertThat(dto.body()).contains("id=12345678"); // ID will be in the body
  }

  /** Generate comma-separated PMID list for testing */
  private String generatePmidList(int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= count; i++) {
      if (i > 1) sb.append(",");
      sb.append(String.format("%08d", 10000000 + i)); // 8-digit PMIDs
    }
    return sb.toString();
  }
}
