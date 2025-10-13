package com.patra.egress.adapter.rest;

import com.patra.egress.api.client.EgressGatewayClient;
import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ExternalCallResponseDTO;
import com.patra.egress.app.usecase.externalcall.ExternalCallCommand;
import com.patra.egress.app.usecase.externalcall.ExternalCallConverter;
import com.patra.egress.app.usecase.externalcall.ExternalCallResult;
import com.patra.egress.app.usecase.externalcall.ExternalCallUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of the egress gateway internal API (Feign client endpoint).
 *
 * <p>Exposes external call capabilities to other microservices via internal RPC contract,
 * delegating to application service and converting DTOs.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExternalCallController implements EgressGatewayClient {

  private final ExternalCallUseCase externalCallUseCase;

  /**
   * Execute an external HTTP call through the egress gateway.
   *
   * @param request the external call request DTO
   * @return the external call response DTO exposed by RPC contract
   */
  @Override
  public ExternalCallResponseDTO call(@Valid @RequestBody ExternalCallRequestDTO request) {
    log.info(
        "[EGRESS][ADAPTER] Received external call request: url={} method={}",
        request.url(),
        request.method());

    // Convert DTO to Command
    ExternalCallCommand command = ExternalCallConverter.toCommand(request);

    // Execute use case
    ExternalCallResult result = externalCallUseCase.execute(command);

    // Convert Result to DTO
    ExternalCallResponseDTO response = ExternalCallConverter.toResponseDTO(result);

    log.info(
        "[EGRESS][ADAPTER] External call completed: traceId={} statusCode={} duration={}ms",
        result.traceId(),
        response.envelope().statusCode(),
        response.durationMs());

    return response;
  }
}
