package com.patra.starter.provenance.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.egress.api.client.EgressGatewayClient;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.converter.XmlToJsonConverter;
import com.patra.starter.provenance.common.gateway.GatewayRequestBuilder;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.common.support.ProvenanceObjectMapperFactory;
import com.patra.starter.provenance.epmc.EPMCClient;
import com.patra.starter.provenance.epmc.EPMCClientImpl;
import com.patra.starter.provenance.epmc.EPMCClientNoOpImpl;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.PubMedClientImpl;
import com.patra.starter.provenance.pubmed.PubMedClientNoOpImpl;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Provenance starter auto-configuration
 * <p>
 * Conditional assembly:
 * 1. @ConditionalOnClass(EgressGatewayClient.class) - Check if gateway API class exists
 * 2. @ConditionalOnProperty - Support enable/disable switch (default enabled)
 * 3. Client beans use @Autowired(required = false) to handle gateway missing scenario
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(ProvenanceProperties.class)
@ConditionalOnClass(EgressGatewayClient.class)  // Check gateway API class exists
@ConditionalOnProperty(prefix = "patra.provenance", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProvenanceAutoConfiguration {

    /**
     * Create the gateway request builder used by all provenance clients.
     *
     * @return gateway request builder bean
     */
    @Bean
    @ConditionalOnMissingBean
    public GatewayRequestBuilder gatewayRequestBuilder() {
        return new GatewayRequestBuilder();
    }

    /**
     * Create the default configuration provider that reads application properties.
     *
     * @param properties bound provenance properties
     * @return default configuration provider
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultConfigProvider defaultConfigProvider(ProvenanceProperties properties) {
        return new DefaultConfigProvider(properties);
    }

    /**
     * Create the XML to JSON converter used by PubMed EFetch.
     *
     * @return converter bean
     */
    @Bean
    @ConditionalOnMissingBean
    public XmlToJsonConverter xmlToJsonConverter() {
        return new XmlToJsonConverter();
    }

    /**
     * Create the shared ObjectMapper for provenance clients.
     *
     * @return configured ObjectMapper bean
     */
    @Bean(name = "provenanceObjectMapper")
    @ConditionalOnMissingBean(name = "provenanceObjectMapper")
    public ObjectMapper provenanceObjectMapper() {
        return ProvenanceObjectMapperFactory.createJsonMapper();
    }

    /**
     * Create the Micrometer-backed metrics recorder when a registry is present.
     *
     * @param meterRegistry Micrometer meter registry
     * @return provenance metrics recorder
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)  // Only register when Micrometer is available
    public ProvenanceMetrics provenanceMetrics(MeterRegistry meterRegistry) {
        return new ProvenanceMetrics(meterRegistry);
    }

    /**
     * Create the PubMed client backed by the egress gateway.
     *
     * @param gatewayClient optional egress gateway client
     * @param requestBuilder gateway request builder bean
     * @param configProvider default configuration provider
     * @param xmlConverter XML to JSON converter
     * @param provenanceObjectMapper shared ObjectMapper bean
     * @param metrics optional metrics recorder
     * @return configured PubMed client implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public PubMedClient pubMedClient(
            @Autowired(required = false) EgressGatewayClient gatewayClient,
            GatewayRequestBuilder requestBuilder,
            DefaultConfigProvider configProvider,
            XmlToJsonConverter xmlConverter,
            ObjectMapper provenanceObjectMapper,
            @Autowired(required = false) ProvenanceMetrics metrics
    ) {
        // Gateway unavailable, return noop implementation
        if (gatewayClient == null) {
            log.warn("[PROVENANCE][BOOT] EgressGatewayClient not available, using no-op PubMedClient");
            return new PubMedClientNoOpImpl();
        }
        return new PubMedClientImpl(gatewayClient, requestBuilder, configProvider, xmlConverter, provenanceObjectMapper, metrics);
    }

    /**
     * Create the Europe PMC client backed by the egress gateway.
     *
     * @param gatewayClient optional egress gateway client
     * @param requestBuilder gateway request builder bean
     * @param configProvider default configuration provider
     * @param provenanceObjectMapper shared ObjectMapper bean
     * @param metrics optional metrics recorder
     * @return configured Europe PMC client implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public EPMCClient epmcClient(
            @Autowired(required = false) EgressGatewayClient gatewayClient,
            GatewayRequestBuilder requestBuilder,
            DefaultConfigProvider configProvider,
            ObjectMapper provenanceObjectMapper,
            @Autowired(required = false) ProvenanceMetrics metrics
    ) {
        // Gateway unavailable, return noop implementation
        if (gatewayClient == null) {
            log.warn("[PROVENANCE][BOOT] EgressGatewayClient not available, using no-op EPMCClient");
            return new EPMCClientNoOpImpl();
        }
        return new EPMCClientImpl(gatewayClient, requestBuilder, configProvider, provenanceObjectMapper, metrics);
    }
}

