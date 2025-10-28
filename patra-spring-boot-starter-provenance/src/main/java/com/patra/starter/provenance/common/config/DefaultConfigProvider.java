package com.patra.starter.provenance.common.config;

import com.patra.starter.provenance.boot.ProvenanceProperties;
import com.patra.starter.provenance.boot.ProvenanceProperties.SourceProperties;
import java.util.Objects;

/**
 * Default configuration provider backed by {@link ProvenanceProperties}.
 *
 * <p>Provides immutable {@link ProvenanceConfig} instances for data sources registered in the
 * configuration map.
 */
public class DefaultConfigProvider {

  private final ProvenanceProperties properties;

  public DefaultConfigProvider(ProvenanceProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties cannot be null");
  }

  /**
   * Returns the default configuration for PubMed.
   *
   * @return immutable configuration for PubMed
   */
  public ProvenanceConfig getPubMedDefaultConfig() {
    return getDefaultConfig("pubmed");
  }

  /**
   * Returns the default configuration for Europe PMC.
   *
   * @return immutable configuration for Europe PMC
   */
  public ProvenanceConfig getEPMCDefaultConfig() {
    return getDefaultConfig("epmc");
  }

  /**
   * Returns the default configuration for the specified provenance code.
   *
   * @param provenanceCode provenance identifier
   * @return immutable configuration
   * @throws IllegalStateException if configuration is missing required fields (e.g. baseUrl)
   */
  public ProvenanceConfig getDefaultConfig(String provenanceCode) {
    SourceProperties source = properties.getConfigForSource(provenanceCode);
    try {
      return source.toProvenanceConfig();
    } catch (IllegalStateException ex) {
      throw new IllegalStateException(
          "Invalid configuration for provenance source: " + provenanceCode, ex);
    }
  }
}
