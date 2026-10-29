package dev.patra.ingest.app.usecase.relay;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import dev.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import dev.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import dev.patra.ingest.app.usecase.relay.dto.RelayReport;
import dev.patra.ingest.app.usecase.relay.executor.OutboxRelayExecutor;
import dev.patra.ingest.app.usecase.relay.planner.RelayPlanBuilder;
import dev.patra.ingest.app.usecase.relay.publisher.RelayEventPublisher;
import dev.patra.ingest.domain.model.vo.relay.RelayBatchResult;
import dev.patra.ingest.domain.model.vo.relay.RelayPlan;
import dev.patra.tool.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// Outbox 中继处理器：提供单批次发布的编排入口。
///
/// 执行流程：
///
/// - 验证功能开关
/// - 构建中继计划
/// - 执行消息发布
/// - 发布领域事件
///
/// 事务语义：不整体包事务（transaction.md 第 3 节）。领取租约、标记状态、写中继日志各用一个短事务，
/// 发到 MQ 在事务外；投递语义是「至少一次」，标记失败时消息租约过期后会被重新领取。
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayHandler implements CommandHandler<OutboxRelayCommand, RelayReport> {

  private final OutboxRelayProperties properties;
  private final RelayPlanBuilder planBuilder;
  private final OutboxRelayExecutor relayExecutor;
  private final RelayEventPublisher eventPublisher;

  /// 执行一轮中继任务。当功能开关关闭时，返回空报告以保持调度器健康。
  ///
  /// 支持针对特定通道或在 `command.channel()` 为 `null` 时处理所有通道。
  ///
  /// @param command 中继命令，允许可选的覆盖配置
  /// @return 执行报告
  @Override
  public RelayReport handle(OutboxRelayCommand command) {
    if (!properties.isEnabled()) {
      var channelKey = command.channel() != null ? command.channel() : null; // null 表示所有通道
      String channelDesc = channelKey != null ? channelKey.channel() : "ALL_CHANNELS";
      log.info("Outbox 中继已禁用，跳过通道={}", channelDesc);
      return RelayReport.empty(channelKey);
    }
    // 记录开始时间戳以计算延迟指标
    TimeInterval timer = DateUtil.timer();

    RelayPlan plan = planBuilder.build(command);
    if (log.isDebugEnabled()) {
      String channelDesc = plan.channel() != null ? plan.channel().channel() : "ALL_CHANNELS";
      log.debug(
          "中继计划已构建 通道={} 批大小={} 租约持有者={} 租约过期时间={}",
          channelDesc,
          plan.batchSize(),
          plan.leaseOwner(),
          plan.leaseExpireAt());
    }

    RelayBatchResult result = relayExecutor.execute(plan);
    eventPublisher.publish(result.events());

    String channelDesc = result.channel() != null ? result.channel().channel() : "ALL_CHANNELS";
    log.info(
        "中继完成 通道={} 获取={} 已发布={} 已重试={} 失败={} 租约丢失={} 耗时毫秒={}",
        channelDesc,
        result.fetched(),
        result.published(),
        result.retried(),
        result.failed(),
        result.leaseMissed(),
        timer.interval());
    return new RelayReport(
        result.channel(),
        result.fetched(),
        result.published(),
        result.retried(),
        result.failed(),
        result.leaseMissed());
  }
}
