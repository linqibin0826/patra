package com.patra.egress.app.usecase.externalcall;

/**
 * External call use case interface Orchestrates external service calls with resilience capabilities
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExternalCallUseCase {

  /**
   * Execute external call with resilience capabilities
   *
   * @param command external call command
   * @return external call result
   */
  ExternalCallResult execute(ExternalCallCommand command);
}
