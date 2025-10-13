package com.patra.egress.app.usecase.externalcall;

import com.patra.egress.domain.model.vo.HttpRequest;
import com.patra.egress.domain.model.vo.ResilienceConfig;

/**
 * External call command Contains HTTP request and optional caller-provided resilience config
 *
 * @param request HTTP request
 * @param callerConfig caller-provided resilience config (optional, can be null)
 * @author linqibin
 * @since 0.1.0
 */
public record ExternalCallCommand(HttpRequest request, ResilienceConfig callerConfig) {
  /** Constructor ensuring immutability */
  public ExternalCallCommand {
    if (request == null) {
      throw new IllegalArgumentException("Request cannot be null");
    }
    // callerConfig is optional, can be null
  }
}
