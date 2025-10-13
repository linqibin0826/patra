package com.patra.ingest.app.usecase.relay;

import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;

/**
 * Contract for the Outbox Relay use case (application-layer orchestration entry point).
 *
 * <p>Responsibilities: accept {@link OutboxRelayCommand} triggered by schedulers or operators,
 * execute one batch publication run for Outbox messages, and return aggregated metrics.
 *
 * <p>Semantics: the call does not perform unbounded retries; failed or pending messages are
 * recovered through domain state and subsequent scheduler cycles.
 */
public interface OutboxRelayUseCase {

  /**
   * Execute a single Outbox relay run.
   *
   * <p>Implementations must guarantee:
   *
   * <ul>
   *   <li>Idempotency: identical messages are not published twice (enforced via leases and state
   *       transitions).
   *   <li>Observability: key counters are emitted to logs.
   * </ul>
   *
   * @param instruction scheduling instruction, allowing overrides on default configuration
   * @return execution report with aggregated statistics
   */
  RelayReport relay(OutboxRelayCommand instruction);
}
