package com.patra.egress.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * Egress Gateway main configuration properties Maps to patra.egress configuration namespace
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "patra.egress")
public class EgressProperties {

  /** Global settings (rate limit, etc.) */
  @NestedConfigurationProperty private GlobalProperties global = new GlobalProperties();

  /** Resilience configuration (max values and defaults) */
  @NestedConfigurationProperty private ResilienceProperties resilience = new ResilienceProperties();
}
