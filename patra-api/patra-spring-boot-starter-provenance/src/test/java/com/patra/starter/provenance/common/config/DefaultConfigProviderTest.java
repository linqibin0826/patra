package com.patra.starter.provenance.common.config;

import com.patra.starter.provenance.boot.ProvenanceProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConfigProviderTest {

    @Test
    void shouldNormalizeBaseUrlAndCopyHeaders() {
        ProvenanceProperties properties = new ProvenanceProperties();
        properties.getPubmed().setBaseUrl("https://example.org/api/");
        properties.getPubmed().getHttp().setDefaultHeaders(Map.of("User-Agent", "Papertrace"));
        properties.getPubmed().getRateLimit().setPerCredentialQpsLimit(10);
        DefaultConfigProvider provider = new DefaultConfigProvider(properties);

        ProvenanceConfig config = provider.getPubMedDefaultConfig();

        assertThat(config.baseUrl()).isEqualTo("https://example.org/api");
        assertThat(config.http().defaultHeaders()).containsEntry("User-Agent", "Papertrace");
        assertThat(config.rateLimit().perCredentialQpsLimit()).isEqualTo(10);
    }
}
