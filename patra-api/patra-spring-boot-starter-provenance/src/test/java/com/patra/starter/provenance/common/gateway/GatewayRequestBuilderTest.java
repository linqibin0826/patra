package com.patra.starter.provenance.common.gateway;

import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.starter.provenance.common.config.HttpConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.config.RateLimitConfig;
import com.patra.starter.provenance.common.config.RetryConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRequestBuilderTest {

    private final GatewayRequestBuilder builder = new GatewayRequestBuilder();

    @Test
    void buildShouldEncodeQueryHeadersAndResilience() {
        ApiRequest request = () -> Map.of(
            "db", "pubmed",
            "term", "cancer therapy"
        );
        ProvenanceConfig config = new ProvenanceConfig(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/",
            new HttpConfig(Map.of("User-Agent", "Papertrace/0.1.0"), 1500, 2000, 2500),
            null,
            null,
            null,
            new RetryConfig(2, 500),
            new RateLimitConfig(null, 5)
        );

        ExternalCallRequestDTO dto = builder.build(
            config.baseUrl(),
            "/esearch.fcgi",
            request,
            config
        );

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
}
