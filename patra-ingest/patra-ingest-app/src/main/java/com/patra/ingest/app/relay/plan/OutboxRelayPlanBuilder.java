package com.patra.ingest.app.relay.plan;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.patra.ingest.app.relay.command.OutboxRelayInstruction;
import com.patra.ingest.app.relay.config.OutboxRelayProperties;
import com.patra.ingest.domain.model.value.RelayPlan;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 根据调度指令与默认配置构建领域层使用的 {@link RelayPlan}。
 */
@Component
public class OutboxRelayPlanBuilder {

    private final OutboxRelayProperties properties;
    private final Clock clock;

    public OutboxRelayPlanBuilder(OutboxRelayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public RelayPlan build(OutboxRelayInstruction instruction) {
        String channel = StrUtil.isNotBlank(instruction.channel())
                ? instruction.channel()
                : properties.getDefaultChannel();
        Instant triggeredAt = instruction.triggeredAt() != null ? instruction.triggeredAt() : Instant.now(clock);
        int batchSize = normalizePositive(instruction.batchSize(), properties.getBatchSize());
        Duration leaseDuration = normalizeDuration(instruction.leaseDuration(), properties.getLeaseDuration());
        int maxAttempts = normalizePositive(instruction.maxAttempts(), properties.getMaxAttempts());
        Duration initialBackoff = normalizeDuration(instruction.initialBackoff(), properties.getInitialBackoff());
        String leaseOwner = StrUtil.isNotBlank(instruction.leaseOwner())
                ? instruction.leaseOwner()
                : buildLeaseOwner(triggeredAt);
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

    private String buildLeaseOwner(Instant timestamp) {
        String host = StrUtil.blankToDefault(NetUtil.getLocalHostName(), "unknown");
        return host + '-' + timestamp.toEpochMilli() + '-' + IdUtil.fastSimpleUUID();
    }
}
