package com.patra.ingest.app.usecase.relay.planner;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.patra.common.messaging.ChannelKey;
import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import com.patra.ingest.domain.messaging.IngestPublishingChannels;
import com.patra.ingest.domain.model.vo.RelayPlan;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * RelayPlan 构建器：合并调度指令与默认配置，生成领域执行所需的不可变计划对象。
 * <p>覆盖规则：指令字段优先；为空/非法则回退到 {@link OutboxRelayProperties} 对应默认值。</p>
 */
@Component
public class RelayPlanBuilder {

    private final OutboxRelayProperties properties;
    private final Clock clock;

    public RelayPlanBuilder(OutboxRelayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 构建 RelayPlan。
     *
     * @param instruction 指令（可能为空字段）
     * @return 计划对象
     */
    public RelayPlan build(OutboxRelayCommand instruction) {
        // TODO 需要确认channel的的生成逻辑 （channel其实在数据库中已经存在了， 在outbox表中）
        var channelKey = resolveChannelKey(instruction);
        Instant triggeredAt = instruction.triggeredAt() != null ? instruction.triggeredAt() : Instant.now(clock);
        int batchSize = normalizePositive(instruction.batchSize(), properties.getBatchSize());
        Duration leaseDuration = normalizeDuration(instruction.leaseDuration(), properties.getLeaseDuration());
        int maxAttempts = normalizePositive(instruction.maxAttempts(), properties.getMaxAttempts());
        Duration initialBackoff = normalizeDuration(instruction.initialBackoff(), properties.getInitialBackoff());
        String leaseOwner = StrUtil.isNotBlank(instruction.leaseOwner())
                ? instruction.leaseOwner()
                : buildLeaseOwner(triggeredAt);
        return new RelayPlan(
                channelKey,
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
     * 解析得到 ChannelKey：
     * 优先使用指令提供；否则尝试解析配置的 defaultChannel（支持 "ingest.task.ready" 或别名 "TASK_READY"）；均缺失时使用内置默认。
     */
    private ChannelKey resolveChannelKey(OutboxRelayCommand instruction) {
        if (instruction.channel() != null) {
            return instruction.channel();
        }
        String cfg = properties.getDefaultChannel();
        if (StrUtil.isNotBlank(cfg)) {
            // 1) 尝试按规范字符串解析
            var byChannel = IngestPublishingChannels.fromChannel(cfg);
            if (byChannel.isPresent()) {
                return byChannel.get();
            }
            // 2) 尝试按别名（枚举名）解析
            try {
                return IngestPublishingChannels.valueOf(cfg.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        // 默认回退
        return IngestPublishingChannels.TASK_READY;
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
