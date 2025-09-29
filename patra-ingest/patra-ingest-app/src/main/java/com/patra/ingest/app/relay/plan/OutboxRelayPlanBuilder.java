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
 * RelayPlan 构建器：合并调度指令与默认配置，生成领域执行所需的不可变计划对象。
 * <p>覆盖规则：指令字段优先；为空/非法则回退到 {@link OutboxRelayProperties} 对应默认值。</p>
 */
@Component
public class OutboxRelayPlanBuilder {

    private final OutboxRelayProperties properties;
    private final Clock clock;

    public OutboxRelayPlanBuilder(OutboxRelayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 构建 RelayPlan。
     *
     * @param instruction 指令（可能为空字段）
     * @return 计划对象
     */
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

    /**
     * 如果 candidate 非正或为空则使用 fallback。
     */
    private int normalizePositive(Integer candidate, int fallback) {
        if (candidate == null || candidate <= 0) {
            return fallback;
        }
        return candidate;
    }

    /**
     * 如果 candidate 为空或非正（<=0）则回退。
     */
    private Duration normalizeDuration(Duration candidate, Duration fallback) {
        if (candidate == null || candidate.isNegative() || candidate.isZero()) {
            return fallback;
        }
        return candidate;
    }

    /**
     * 构造租约持有者：host + 触发毫秒 + 随机 UUID。
     */
    private String buildLeaseOwner(Instant timestamp) {
        String host = StrUtil.blankToDefault(NetUtil.getLocalHostName(), "unknown");
        return host + '-' + timestamp.toEpochMilli() + '-' + IdUtil.fastSimpleUUID();
    }
}
