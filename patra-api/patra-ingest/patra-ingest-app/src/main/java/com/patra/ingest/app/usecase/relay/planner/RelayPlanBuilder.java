package com.patra.ingest.app.usecase.relay.planner;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.patra.common.messaging.ChannelKey;
import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builder for {@link RelayPlan}: merges the scheduling instruction with default configuration to
 * produce the immutable plan consumed by the domain layer.
 *
 * <p>Rule precedence: instruction fields win; blank or invalid values fall back to {@link
 * OutboxRelayProperties} defaults.
 *
 * <p>{@code channel == null} indicates that all channels should be processed.
 */
@Slf4j
@Component
public class RelayPlanBuilder {

  private final OutboxRelayProperties properties;
  private final Clock clock;

  public RelayPlanBuilder(OutboxRelayProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Build the relay plan.
   *
   * <p>If {@code instruction.channel()} is {@code null} and no default channel is configured, the
   * plan keeps the channel {@code null} to signal broadcast processing.
   *
   * @param instruction instruction payload (fields may be blank)
   * @return immutable plan instance
   */
  public RelayPlan build(OutboxRelayCommand instruction) {
    // Channel may be null to indicate all channels
    ChannelKey channelKey = resolveChannelKey(instruction);
    Instant triggeredAt =
        instruction.triggeredAt() != null ? instruction.triggeredAt() : Instant.now(clock);
    int batchSize = normalizePositive(instruction.batchSize(), properties.getBatchSize());
    Duration leaseDuration =
        normalizeDuration(instruction.leaseDuration(), properties.getLeaseDuration());
    int maxAttempts = normalizePositive(instruction.maxAttempts(), properties.getMaxAttempts());
    Duration initialBackoff =
        normalizeDuration(instruction.initialBackoff(), properties.getInitialBackoff());
    String leaseOwner =
        StrUtil.isNotBlank(instruction.leaseOwner())
            ? instruction.leaseOwner()
            : buildLeaseOwner(triggeredAt);

    if (log.isDebugEnabled()) {
      String channelDesc = channelKey != null ? channelKey.channel() : "ALL_CHANNELS";
      log.debug(
          "Building relay plan for channel [{}] with batchSize [{}], maxAttempts [{}], leaseDuration [{}ms], initialBackoff [{}ms]",
          channelDesc,
          batchSize,
          maxAttempts,
          leaseDuration.toMillis(),
          initialBackoff.toMillis());
    }

    return new RelayPlan(
        channelKey,
        triggeredAt,
        batchSize,
        leaseDuration,
        maxAttempts,
        initialBackoff,
        properties.getBackoffMultiplier(),
        properties.getMaxBackoff(),
        leaseOwner);
  }

  /** Resolve the {@link ChannelKey} from the instruction; {@code null} means all channels. */
  private ChannelKey resolveChannelKey(OutboxRelayCommand instruction) {
    // Return the channel from the instruction directly; may be null to process all channels
    return instruction.channel();
  }

  /** Use the fallback when the candidate is {@code null} or non-positive. */
  private int normalizePositive(Integer candidate, int fallback) {
    if (candidate == null || candidate <= 0) {
      return fallback;
    }
    return candidate;
  }

  /** Use the fallback when the candidate is {@code null}, negative, or zero. */
  private Duration normalizeDuration(Duration candidate, Duration fallback) {
    if (candidate == null || candidate.isNegative() || candidate.isZero()) {
      return fallback;
    }
    return candidate;
  }

  /** Compose the lease owner identifier as host + trigger epoch millis + random UUID. */
  private String buildLeaseOwner(Instant timestamp) {
    String host = StrUtil.blankToDefault(NetUtil.getLocalHostName(), "unknown");
    return host + '-' + timestamp.toEpochMilli() + '-' + IdUtil.fastSimpleUUID();
  }
}
