package com.patra.starter.provenance.common.config;

import com.patra.starter.provenance.boot.ProvenanceProperties;

/**
 * Default configuration provider.
 * Provides local default configuration from ProvenanceProperties.
 *
 * @author linqibin
 * @since 0.1.0
 */
public class DefaultConfigProvider {

    private final ProvenanceProperties properties;

    public DefaultConfigProvider(ProvenanceProperties properties) {
        this.properties = properties;
    }

    /**
     * Get default config for PubMed
     *
     * @return default ProvenanceConfig
     */
    public ProvenanceConfig getPubMedDefaultConfig() {
        ProvenanceProperties.PubMedProperties pubmed = properties.getPubmed();
        return new ProvenanceConfig(
            pubmed.getBaseUrl(),
            new HttpConfig(
                pubmed.getHttp().getDefaultHeaders(),
                pubmed.getHttp().getTimeoutConnectMillis(),
                pubmed.getHttp().getTimeoutReadMillis(),
                pubmed.getHttp().getTimeoutTotalMillis()
            ),
            null, // pagination - not configured locally
            null, // windowOffset - not configured locally
            null, // batching - not configured locally
            null, // retry - not configured locally
            null  // rateLimit - not configured locally
        );
    }

    /**
     * Get default config for EPMC
     *
     * @return default ProvenanceConfig
     */
    public ProvenanceConfig getEPMCDefaultConfig() {
        ProvenanceProperties.EPMCProperties epmc = properties.getEpmc();
        return new ProvenanceConfig(
            epmc.getBaseUrl(),
            new HttpConfig(
                epmc.getHttp().getDefaultHeaders(),
                epmc.getHttp().getTimeoutConnectMillis(),
                epmc.getHttp().getTimeoutReadMillis(),
                epmc.getHttp().getTimeoutTotalMillis()
            ),
            null, // pagination - not configured locally
            null, // windowOffset - not configured locally
            null, // batching - not configured locally
            null, // retry - not configured locally
            null  // rateLimit - not configured locally
        );
    }
}
