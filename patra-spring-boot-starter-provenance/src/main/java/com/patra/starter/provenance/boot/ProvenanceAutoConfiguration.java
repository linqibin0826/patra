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
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.PubMedClientImpl;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Provenance starter auto-configuration
 *
 * <p>Auto-configures PubMed and Europe PMC clients backed by the egress gateway. The egress gateway
 * API is a required dependency for this starter.
 *
 * <p>The {@link EgressGatewayClient} is automatically discovered via {@code
 * patra-spring-cloud-starter-feign} which scans all {@code @FeignClient} interfaces under {@code
 * com.patra} package.
 *
 * <p>Configuration properties: Use {@code patra.provenance.enabled=false} to disable this
 * auto-configuration if needed.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(ProvenanceProperties.class)
@ConditionalOnProperty(
    prefix = "patra.provenance",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
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
  @ConditionalOnBean(MeterRegistry.class) // Only register when Micrometer is available
  public ProvenanceMetrics provenanceMetrics(MeterRegistry meterRegistry) {
    return new ProvenanceMetrics(meterRegistry);
  }

  /**
   * Create the PubMed client backed by the egress gateway.
   *
   * @param gatewayClient egress gateway client (required)
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
      EgressGatewayClient gatewayClient,
      GatewayRequestBuilder requestBuilder,
      DefaultConfigProvider configProvider,
      XmlToJsonConverter xmlConverter,
      ObjectMapper provenanceObjectMapper,
      @Autowired(required = false) ProvenanceMetrics metrics) {
    return new PubMedClientImpl(
        gatewayClient,
        requestBuilder,
        configProvider,
        xmlConverter,
        provenanceObjectMapper,
        metrics);
  }

  /**
   * Create the Europe PMC client backed by the egress gateway.
   *
   * @param gatewayClient egress gateway client (required)
   * @param requestBuilder gateway request builder bean
   * @param configProvider default configuration provider
   * @param provenanceObjectMapper shared ObjectMapper bean
   * @param metrics optional metrics recorder
   * @return configured Europe PMC client implementation
   */
  @Bean
  @ConditionalOnMissingBean
  public EPMCClient epmcClient(
      EgressGatewayClient gatewayClient,
      GatewayRequestBuilder requestBuilder,
      DefaultConfigProvider configProvider,
      ObjectMapper provenanceObjectMapper,
      @Autowired(required = false) ProvenanceMetrics metrics) {
    return new EPMCClientImpl(
        gatewayClient, requestBuilder, configProvider, provenanceObjectMapper, metrics);
  }
}
