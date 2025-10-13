package com.patra.egress.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Resilience configuration properties Maps to patra.egress.resilience configuration Contains
 * maximum values and default values for resilience settings
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
public class ResilienceProperties {

  /**
   * Maximum values for resilience configuration These are upper bounds that caller configurations
   * cannot exceed
   */
  @NestedConfigurationProperty
  private ResilienceConfigProperties max = new ResilienceConfigProperties();

  /** Default values for resilience configuration Used when caller does not provide configuration */
  @NestedConfigurationProperty
  private ResilienceConfigProperties defaultConfig = new ResilienceConfigProperties();
}
