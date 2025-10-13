package com.patra.egress.infra.config.properties;

import lombok.Data;

/**
 * Global configuration properties Maps to patra.egress.global configuration
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
public class GlobalProperties {

  /** Global rate limit (requests per second) Default: 1000 */
  private int rateLimit = 1000;
}
