package com.patra.egress.adapter.rest;

import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ExternalCallResponseDTO;
import com.patra.egress.app.usecase.externalcall.ExternalCallCommand;
import com.patra.egress.app.usecase.externalcall.ExternalCallConverter;
import com.patra.egress.app.usecase.externalcall.ExternalCallResult;
import com.patra.egress.app.usecase.externalcall.ExternalCallUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * External call REST controller
 * Provides HTTP endpoint for calling external services through the egress gateway
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/egress")
@RequiredArgsConstructor
public class ExternalCallController {

    private final ExternalCallUseCase externalCallUseCase;

    /**
     * Call external service through egress gateway
     *
     * @param request external call request DTO
     * @return external call response DTO
     */
    @PostMapping("/call")
    public ResponseEntity<ExternalCallResponseDTO> call(@Valid @RequestBody ExternalCallRequestDTO request) {
        log.info("[EGRESS][ADAPTER] Received external call request: url={} method={}",
                request.url(), request.method());

        // Convert DTO to Command
        ExternalCallCommand command = ExternalCallConverter.toCommand(request);

        // Execute use case
        ExternalCallResult result = externalCallUseCase.execute(command);

        // Convert Result to DTO
        ExternalCallResponseDTO response = ExternalCallConverter.toResponseDTO(result);

        log.info("[EGRESS][ADAPTER] External call completed: traceId={} statusCode={} duration={}ms",
                result.traceId(), response.envelope().statusCode(), response.durationMs());

        return ResponseEntity.ok(response);
    }
}
