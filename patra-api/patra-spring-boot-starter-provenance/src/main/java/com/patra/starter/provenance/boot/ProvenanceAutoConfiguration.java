package com.patra.starter.provenance.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.http.SimpleHttpClient;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
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
 * <p>Auto-configures PubMed and Europe PMC clients. Outbound HTTP is performed directly by the
 * starter（无独立出站网关服务）。
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

  /** Create the PubMed client (direct HTTP). */
  @Bean
  @ConditionalOnMissingBean
  public PubMedClient pubMedClient(
      DefaultConfigProvider configProvider,
      XmlMapper xmlMapper,
      ObjectMapper objectMapper,
      @Autowired(required = false) ProvenanceMetrics metrics) {
    return new PubMedClientImpl(
        new SimpleHttpClient(), configProvider, objectMapper, xmlMapper, metrics);
  }

  /** Create the Europe PMC client (direct HTTP). */
  @Bean
  @ConditionalOnMissingBean
  public EPMCClient epmcClient(
      DefaultConfigProvider configProvider,
      ObjectMapper objectMapper,
      @Autowired(required = false) ProvenanceMetrics metrics) {
    return new EPMCClientImpl(new SimpleHttpClient(), configProvider, objectMapper, metrics);
  }
}
