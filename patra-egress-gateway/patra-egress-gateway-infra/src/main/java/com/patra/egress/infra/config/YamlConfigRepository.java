package com.patra.egress.infra.config;

import com.patra.egress.domain.model.vo.ResilienceConfig;
import com.patra.egress.domain.port.ConfigPort;
import com.patra.egress.infra.config.properties.EgressProperties;
import com.patra.egress.infra.config.properties.ResilienceConfigProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * YAML-based configuration repository implementation Loads resilience configuration from
 * application YAML files
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class YamlConfigRepository implements ConfigPort {

  private final EgressProperties egressProperties;

  @Override
  public ResilienceConfig loadSystemDefaultConfig() {
    log.debug("[EGRESS][INFRA] Loading system default resilience configuration");
    ResilienceConfigProperties defaultProps = egressProperties.getResilience().getDefaultConfig();
    return convertToResilienceConfig(defaultProps);
  }

  @Override
  public ResilienceConfig loadSystemMaxConfig() {
    log.debug("[EGRESS][INFRA] Loading system max resilience configuration");
    ResilienceConfigProperties maxProps = egressProperties.getResilience().getMax();
    return convertToResilienceConfig(maxProps);
  }

  /**
   * Convert ResilienceConfigProperties to ResilienceConfig domain value object
   *
   * @param props Configuration properties
   * @return ResilienceConfig domain value object
   */
  private ResilienceConfig convertToResilienceConfig(ResilienceConfigProperties props) {
    // Create immutable copy of whitelist headers
    List<String> whitelistHeaders =
        props.getWhitelistResponseHeaders() != null
            ? List.copyOf(props.getWhitelistResponseHeaders())
            : List.of();

    return new ResilienceConfig(
        props.getTimeout(),
        props.getMaxRetries(),
        props.getRetryBackoff(),
        props.getRateLimit(),
        props.getCircuitBreakerFailureThreshold(),
        props.getCircuitBreakerWaitDuration(),
        whitelistHeaders);
  }
}
