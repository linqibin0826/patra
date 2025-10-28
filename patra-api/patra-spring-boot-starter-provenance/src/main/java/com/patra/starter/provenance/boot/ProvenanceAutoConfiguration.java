package com.patra.starter.provenance.boot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.patra.starter.provenance.common.adapter.AdapterRegistry;
import com.patra.starter.provenance.common.adapter.DataSourceAdapter;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.http.SimpleHttpClient;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.epmc.EPMCClient;
import com.patra.starter.provenance.epmc.EPMCClientImpl;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.PubMedClientImpl;
import com.patra.starter.provenance.pubmed.PubmedDataSourceAdapter;
import com.patra.starter.provenance.pubmed.converter.PubmedArticleConverter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
   * Creates the default configuration provider that reads application properties.
   *
   * @param properties bound provenance properties
   * @return default configuration provider
   */
  @Bean
  @ConditionalOnMissingBean
  public DefaultConfigProvider defaultConfigProvider(ProvenanceProperties properties) {
    log.info("Initializing Provenance configuration provider with PubMed and EPMC defaults");
    return new DefaultConfigProvider(properties);
  }

  /**
   * Creates the Micrometer-backed metrics recorder when a registry is present.
   *
   * @param meterRegistry Micrometer meter registry
   * @return provenance metrics recorder
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(MeterRegistry.class)
  public ProvenanceMetrics provenanceMetrics(MeterRegistry meterRegistry) {
    log.info("Enabling Provenance metrics instrumentation with Micrometer");
    return new ProvenanceMetrics(meterRegistry);
  }

  /**
   * Registers the adapter registry, allowing ingest to discover available data source adapters.
   *
   * @param adaptersProvider provider for adapter implementations
   * @return adapter registry
   */
  @Bean
  @ConditionalOnMissingBean
  public AdapterRegistry adapterRegistry(ObjectProvider<List<DataSourceAdapter>> adaptersProvider) {
    List<DataSourceAdapter> adapters = adaptersProvider.getIfAvailable(List::of);
    return new AdapterRegistry(adapters);
  }

  /**
   * Creates the XML mapper for parsing PubMed XML responses.
   *
   * <p>Configured to handle PubMed's XML format with lenient parsing options.
   *
   * @return configured XML mapper
   */
  @Bean
  @ConditionalOnMissingBean
  public XmlMapper provenanceXmlMapper() {
    return XmlMapper.builder()
        .findAndAddModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .defaultUseWrapper(false)
        .build();
  }

  /**
   * Creates the PubMed client for direct HTTP access to E-utilities API.
   *
   * @param configProvider configuration provider for PubMed settings
   * @param xmlMapper XML mapper for parsing PubMed XML responses
   * @param objectMapper JSON mapper for parsing PubMed JSON responses
   * @param metrics optional metrics recorder
   * @return PubMed client implementation
   */
  @Bean
  @ConditionalOnMissingBean
  public PubMedClient pubMedClient(
      DefaultConfigProvider configProvider,
      XmlMapper xmlMapper,
      ObjectMapper objectMapper,
      Optional<ProvenanceMetrics> metrics) {
    log.info("Auto-configuring PubMed client for E-utilities API access");
    return new PubMedClientImpl(
        new SimpleHttpClient(), configProvider, objectMapper, xmlMapper, metrics.orElse(null));
  }

  /**
   * Creates the Europe PMC client for direct HTTP access to EPMC API.
   *
   * @param configProvider configuration provider for EPMC settings
   * @param objectMapper JSON mapper for parsing EPMC responses
   * @param metrics optional metrics recorder
   * @return Europe PMC client implementation
   */
  @Bean
  @ConditionalOnMissingBean
  public EPMCClient epmcClient(
      DefaultConfigProvider configProvider,
      ObjectMapper objectMapper,
      Optional<ProvenanceMetrics> metrics) {
    log.info("Auto-configuring Europe PMC client for EPMC API access");
    return new EPMCClientImpl(
        new SimpleHttpClient(), configProvider, objectMapper, metrics.orElse(null));
  }

  /**
   * Registers the PubMed data source adapter so ingest can consume PubMed via the unified adapter
   * contract.
   *
   * @param pubMedClient PubMed client
   * @param articleConverter article converter
   * @param properties provenance properties for configuration merging
   * @return PubMed data source adapter
   */
  @Bean
  @ConditionalOnMissingBean
  public PubmedDataSourceAdapter pubmedDataSourceAdapter(
      PubMedClient pubMedClient,
      PubmedArticleConverter articleConverter,
      ProvenanceProperties properties,
      Optional<ProvenanceMetrics> metrics) {
    return new PubmedDataSourceAdapter(
        pubMedClient, articleConverter, properties, metrics.orElse(null));
  }
}
