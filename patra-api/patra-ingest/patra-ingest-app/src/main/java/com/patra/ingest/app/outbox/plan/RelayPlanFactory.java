package com.patra.ingest.app.outbox.plan;

import com.patra.ingest.app.outbox.command.OutboxRelayInstruction;
import com.patra.ingest.app.outbox.config.OutboxRelayProperties;
import com.patra.ingest.domain.model.value.RelayPlan;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 将调度指令与默认配置合并得到领域层使用的 RelayPlan。
 */
@Component
public class RelayPlanFactory {

    private final OutboxRelayProperties properties;
    private final Clock clock;

    public RelayPlanFactory(OutboxRelayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public RelayPlan create(OutboxRelayInstruction instruction) {
        String channel = StringUtils.hasText(instruction.channel())
                ? instruction.channel()
                : properties.getDefaultChannel();
        Instant triggeredAt = instruction.triggeredAt() != null ? instruction.triggeredAt() : Instant.now(clock);
        int batchSize = normalizePositive(instruction.batchSize(), properties.getBatchSize());
        Duration leaseDuration = normalizeDuration(instruction.leaseDuration(), properties.getLeaseDuration());
        int maxAttempts = normalizePositive(instruction.maxAttempts(), properties.getMaxAttempts());
        Duration initialBackoff = normalizeDuration(instruction.initialBackoff(), properties.getInitialBackoff());
        String leaseOwner = StringUtils.hasText(instruction.leaseOwner())
                ? instruction.leaseOwner()
                : buildLeaseOwner();
        return new RelayPlan(
                channel,
                triggeredAt,
                batchSize,
                leaseDuration,
                maxAttempts,
                initialBackoff,
                properties.getBackoffMultiplier(),
                properties.getMaxBackoff(),
                leaseOwner
        );
    }

    private int normalizePositive(Integer candidate, int fallback) {
        if (candidate == null || candidate <= 0) {
            return fallback;
        }
        return candidate;
    }

    private Duration normalizeDuration(Duration candidate, Duration fallback) {
        if (candidate == null || candidate.isNegative() || candidate.isZero()) {
            return fallback;
        }
        return candidate;
    }

    private String buildLeaseOwner() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            return host + "-" + Instant.now(clock).toEpochMilli();
        } catch (UnknownHostException ex) {
            return "unknown-" + Instant.now(clock).toEpochMilli();
        }
    }
}
