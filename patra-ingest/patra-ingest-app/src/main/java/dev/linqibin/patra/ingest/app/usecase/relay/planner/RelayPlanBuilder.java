package dev.linqibin.patra.ingest.app.usecase.relay.planner;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import dev.linqibin.commons.messaging.ChannelKey;
import dev.linqibin.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import dev.linqibin.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import dev.linqibin.patra.ingest.domain.model.vo.relay.RelayPlan;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// {@link RelayPlan} 构建器: 合并调度指令与默认配置,生成领域层使用的不可变计划
///
/// 规则优先级: 指令字段优先;空白或无效值回退到 {@link OutboxRelayProperties} 默认值
///
/// `channel == null` 表示应处理所有通道
@Slf4j
@Component
public class RelayPlanBuilder {

  private final OutboxRelayProperties properties;
  private final Clock clock;

  public RelayPlanBuilder(OutboxRelayProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  /// 构建中继计划
  ///
  /// 如果 `instruction.channel()` 为 `null` 且未配置默认通道, 则计划保持通道为 `null` 以表示广播处理
  ///
  /// @param instruction 指令负载 (字段可能为空)
  /// @return 不可变的计划实例
  public RelayPlan build(OutboxRelayCommand instruction) {
    // 通道可能为 null 表示所有通道
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
          "构建中继计划 通道 [{}] 批大小 [{}], 最大尝试次数 [{}], 租约持续时间 [{}ms], 初始退避 [{}ms]",
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

  /// 从指令解析 {@link ChannelKey}; `null` 表示所有通道
  private ChannelKey resolveChannelKey(OutboxRelayCommand instruction) {
    // 直接返回指令中的通道;可能为 null 以处理所有通道
    return instruction.channel();
  }

  /// 当候选值为 `null` 或非正数时使用回退值
  private int normalizePositive(Integer candidate, int fallback) {
    if (candidate == null || candidate <= 0) {
      return fallback;
    }
    return candidate;
  }

  /// 当候选值为 `null`、负数或零时使用回退值
  private Duration normalizeDuration(Duration candidate, Duration fallback) {
    if (candidate == null || candidate.isNegative() || candidate.isZero()) {
      return fallback;
    }
    return candidate;
  }

  /// 组合租约持有者标识符: 主机 + 触发 epoch 毫秒 + 随机 UUID
  private String buildLeaseOwner(Instant timestamp) {
    String host = StrUtil.blankToDefault(NetUtil.getLocalHostName(), "unknown");
    return host + '-' + timestamp.toEpochMilli() + '-' + IdUtil.fastSimpleUUID();
  }
}
