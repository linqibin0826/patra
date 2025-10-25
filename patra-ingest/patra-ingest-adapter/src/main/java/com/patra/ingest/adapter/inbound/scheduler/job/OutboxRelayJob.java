package com.patra.ingest.adapter.inbound.scheduler.job;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.messaging.ChannelKey;
import com.patra.ingest.adapter.inbound.scheduler.param.OutboxRelayJobParam;
import com.patra.ingest.app.usecase.relay.OutboxRelayUseCase;
import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;
import com.patra.ingest.domain.exception.IngestScheduleParameterException;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.messaging.IngestPublishingChannels;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbox Relay scheduled job. Periodically scans the Outbox table to fetch deliverable messages and
 * attempts to publish them.
 *
 * <p>Workflow: parse params → build command (with lease/retry settings) → invoke use case → report
 * result.
 *
 * <p>Idempotency: the lease owner identifier includes host + jobId + threadId + uuid to distinguish
 * concurrent instances.
 *
 * <p>Failure mode: business failures are wrapped into {@link OutboxRelayExecutionException} and
 * rethrown; XXL marks the job failed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

  private final ObjectMapper objectMapper;
  private final OutboxRelayUseCase relayUseCase;
  private final OutboxRelayProperties relayProperties;
  private final Clock clock;

  /**
   * XXL-Job entrypoint. Parses params, performs relay, and writes statistics to the scheduler log.
   *
   * <p>Supports a specific channel or all channels when param is blank.
   */
  @XxlJob("ingestOutboxRelayJob")
  public void execute() {
    Instant now = Instant.now(clock);
    try {
      OutboxRelayJobParam jobParam = parseParam(XxlJobHelper.getJobParam());
      OutboxRelayCommand command = buildInstruction(jobParam, now);
      RelayReport report = relayUseCase.relay(command);
      handleRelaySuccess(report);
    } catch (OutboxRelayExecutionException ex) {
      throw ex;
    } catch (Exception ex) {
      handleRelayFailure(ex);
      throw new OutboxRelayExecutionException("Outbox relay execution failed", ex);
    }
  }

  /** Handles successful relay execution with result reporting and logging. */
  private void handleRelaySuccess(RelayReport report) {
    String channelDesc = report.channel() != null ? report.channel().channel() : "ALL_CHANNELS";

    log.info(
        "Completed outbox relay for channel [{}]: {} messages fetched, {} published, {} retried, {} failed, {} lease missed",
        channelDesc,
        report.fetched(),
        report.published(),
        report.retried(),
        report.failed(),
        report.leaseMissed());

    XxlJobHelper.handleSuccess(
        String.format(
            "Relay finished channel=%s fetched=%d published=%d retried=%d failed=%d leaseMissed=%d",
            channelDesc,
            report.fetched(),
            report.published(),
            report.retried(),
            report.failed(),
            report.leaseMissed()));
  }

  /** Handles relay failure with error logging and reporting. */
  private void handleRelayFailure(Exception ex) {
    log.error("Failed to execute outbox relay job: {}", ex.getMessage(), ex);
    XxlJobHelper.handleFail("Relay failed: " + ex.getMessage());
  }

  /**
   * Builds the relay command: target channel, time base, batch size, lease configuration, and retry
   * policy.
   *
   * @param param job parameters (fields may be null)
   * @param now current time (from injected Clock for testability)
   * @return relay command
   */
  private OutboxRelayCommand buildInstruction(OutboxRelayJobParam param, Instant now) {
    return new OutboxRelayCommand(
        resolveChannel(param.channel()),
        now,
        param.batchSize(),
        parseDuration(param.leaseDuration()),
        param.maxAttempts(),
        parseDuration(param.initialBackoff()),
        buildLeaseOwner());
  }

  /** Parses the channel; null/blank falls back to default configured channel. */
  private ChannelKey resolveChannel(String channel) {
    if (CharSequenceUtil.isBlank(channel)) {
      return null; // Let the builder fall back to its default value
    }
    String trimmed = CharSequenceUtil.trim(channel);
    var byChannel = IngestPublishingChannels.fromChannel(trimmed);
    if (byChannel.isPresent()) {
      return byChannel.get();
    }
    try {
      return IngestPublishingChannels.valueOf(trimmed.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IngestScheduleParameterException("Illegal channel value: " + channel, ex);
    }
  }

  /**
   * Parses a duration string: supports ISO-8601 (starting with PT) or a plain numeric seconds
   * string.
   *
   * @param value duration string
   * @return Duration or null when blank
   * @throws IngestScheduleParameterException when format is illegal
   */
  private Duration parseDuration(String value) {
    if (CharSequenceUtil.isBlank(value)) {
      return null;
    }
    String trimmed = CharSequenceUtil.trim(value);
    try {
      if (trimmed.startsWith("PT")) {
        return Duration.parse(trimmed);
      }
      return Duration.ofSeconds(Long.parseLong(trimmed));
    } catch (Exception ex) {
      throw new IngestScheduleParameterException("Illegal duration value: " + value, ex);
    }
  }

  /** Parses JSON param; throws schedule parameter exception on failure. */
  private OutboxRelayJobParam parseParam(String param) {
    if (CharSequenceUtil.isBlank(param)) {
      return new OutboxRelayJobParam(null, null, null, null, null);
    }
    try {
      return objectMapper.readValue(param, OutboxRelayJobParam.class);
    } catch (Exception ex) {
      throw new IngestScheduleParameterException(
          "Failed to parse relay param: " + ex.getMessage(), ex);
    }
  }

  /**
   * Builds the lease owner id: host + jobId + threadId + uuid to avoid collisions and aid
   * traceability.
   */
  private String buildLeaseOwner() {
    String host = CharSequenceUtil.blankToDefault(NetUtil.getLocalHostName(), "unknown");
    return host
        + '-'
        + XxlJobHelper.getJobId()
        + '-'
        + Thread.currentThread().threadId()
        + '-'
        + IdUtil.fastSimpleUUID();
  }
}
