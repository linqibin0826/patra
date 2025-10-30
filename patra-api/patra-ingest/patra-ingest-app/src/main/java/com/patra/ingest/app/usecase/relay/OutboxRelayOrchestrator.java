package com.patra.ingest.app.usecase.relay;

import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;
import com.patra.ingest.app.usecase.relay.executor.OutboxRelayExecutor;
import com.patra.ingest.app.usecase.relay.planner.RelayPlanBuilder;
import com.patra.ingest.app.usecase.relay.publisher.RelayEventPublisher;
import com.patra.ingest.domain.model.vo.RelayBatchResult;
import com.patra.ingest.domain.model.vo.RelayPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Outbox Relay use case: exposes the orchestration entry for a single
 * batch publication.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>Check the feature toggle (disabled -> return an empty report to avoid scheduler errors).
 *   <li>Build a {@link RelayPlan}.
 *   <li>Delegate to the executor and gather the {@link RelayBatchResult}.
 *   <li>Publish domain events for auditing and monitoring.
 *   <li>Assemble and return the {@link RelayReport}.
 * </ol>
 *
 * Transaction semantics: the method is annotated with {@code @Transactional} (single-database write
 * atomicity), while the executor updates message state inside the same boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayOrchestrator implements OutboxRelayUseCase {

  private final OutboxRelayProperties properties;
  private final RelayPlanBuilder planBuilder;
  private final OutboxRelayExecutor relayExecutor;
  private final RelayEventPublisher eventPublisher;

  /**
   * Execute one relay run. When the feature flag is off, return an empty report to keep the
   * scheduler healthy.
   *
   * <p>Supports targeting a specific channel or processing all channels when {@code
   * instruction.channel()} is {@code null}.
   *
   * @param instruction instruction payload allowing optional overrides
   * @return execution report
   */
  @Override
  @Transactional
  public RelayReport relay(OutboxRelayCommand instruction) {
    if (!properties.isEnabled()) {
      var channelKey =
          instruction.channel() != null ? instruction.channel() : null; // null means all channels
      String channelDesc = channelKey != null ? channelKey.channel() : "ALL_CHANNELS";
      log.info("Outbox relay disabled, skip channel={}", channelDesc);
      return RelayReport.empty(channelKey);
    }
    // Record start timestamp to compute latency metrics
    long start = System.currentTimeMillis();

    RelayPlan plan = planBuilder.build(instruction);
    if (log.isDebugEnabled()) {
      String channelDesc = plan.channel() != null ? plan.channel().channel() : "ALL_CHANNELS";
      log.debug(
          "relay plan built channel={} batchSize={} leaseOwner={} leaseExpireAt={}",
          channelDesc,
          plan.batchSize(),
          plan.leaseOwner(),
          plan.leaseExpireAt());
    }

    RelayBatchResult result = relayExecutor.execute(plan);
    eventPublisher.publish(result.events());

    long elapsed = System.currentTimeMillis() - start;
    String channelDesc = result.channel() != null ? result.channel().channel() : "ALL_CHANNELS";
    log.info(
        "relay completed channel={} fetched={} published={} retried={} failed={} leaseMissed={} costMs={}",
        channelDesc,
        result.fetched(),
        result.published(),
        result.retried(),
        result.failed(),
        result.leaseMissed(),
        elapsed);
    return new RelayReport(
        result.channel(),
        result.fetched(),
        result.published(),
        result.retried(),
        result.failed(),
        result.leaseMissed());
  }
}
