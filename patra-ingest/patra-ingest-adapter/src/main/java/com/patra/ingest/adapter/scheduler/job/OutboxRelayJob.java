package com.patra.ingest.adapter.scheduler.job;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import com.patra.common.cqrs.CommandBus;
import com.patra.common.messaging.ChannelKey;
import com.patra.ingest.adapter.scheduler.param.OutboxRelayJobParam;
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
import tools.jackson.databind.ObjectMapper;

/// Outbox 消息中继定时任务。
///
/// 定期扫描 Outbox 表以获取待投递的消息并尝试发布到 RocketMQ。实现事务性发件箱模式(Transactional Outbox Pattern),确保消息最终一致性投递。
///
/// 执行流程:
///
/// - 1. 解析 XXL-Job 参数(通道、批量大小、租约时长、重试策略等)
///   - 2. 构建租约所有者标识符(host + jobId + threadId + uuid)
///   - 3. 构建 OutboxRelayCommand 并委托给应用层用例
///   - 4. 用例执行: 获取租约 → 发布消息 → 更新状态 → 释放租约
///   - 5. 报告执行结果(获取/发布/重试/失败统计)
///
/// 幂等性保证: 租约所有者标识符包含 hostname + jobId + threadId + uuid,确保并发实例之间不会冲突,同一条消息同一时间只能被一个执行器处理。
///
/// 失败处理: 业务层失败会被包装为 {@link OutboxRelayExecutionException} 并重新抛出,由 XXL-Job 根据重试策略决定是否重试任务。
///
/// 设计模式: 事务性发件箱模式 + 租约机制 - 保证消息最终一致性投递和分布式环境下的并发安全。
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

  private final ObjectMapper objectMapper;
  private final CommandBus commandBus;
  private final OutboxRelayProperties relayProperties;
  private final Clock clock;

  /// XXL-Job 入口点。解析参数、执行转发并将统计信息写入调度器日志。
  ///
  /// 支持特定通道或在参数为空时支持所有通道。
  @XxlJob("ingestOutboxRelayJob")
  public void execute() {
    String rawParam = XxlJobHelper.getJobParam();
    log.debug("Outbox relay 任务已触发,jobId [{}],参数: {}", XxlJobHelper.getJobId(), rawParam);

    Instant now = Instant.now(clock);
    try {
      OutboxRelayJobParam jobParam = parseParam(rawParam);
      log.debug(
          "已解析 outbox relay 参数: 通道 [{}], 批量大小 [{}], 租约持续时间 [{}], 最大尝试次数 [{}], 初始退避 [{}]",
          jobParam.channel(),
          jobParam.batchSize(),
          jobParam.leaseDuration(),
          jobParam.maxAttempts(),
          jobParam.initialBackoff());

      OutboxRelayCommand command = buildInstruction(jobParam, now);
      log.debug("已构建 relay 命令,租约所有者 [{}],时间 {}", command.leaseOwner(), now);

      RelayReport report = commandBus.handle(command);
      handleRelaySuccess(report);
    } catch (OutboxRelayExecutionException ex) {
      handleRelayFailure(ex);
      // 已通过 handleFail 报告失败,不再抛出异常
    } catch (Exception ex) {
      handleRelayFailure(ex);
      // 已通过 handleFail 报告失败,不再抛出异常
    }
  }

  /// 处理成功的 relay 执行,包含结果报告和日志记录。
  private void handleRelaySuccess(RelayReport report) {
    String channelDesc = report.channel() != null ? report.channel().channel() : "ALL_CHANNELS";

    log.info(
        "已完成通道 [{}] 的 outbox relay: 获取 {} 条消息,已发布 {},已重试 {},失败 {},错过租约 {}",
        channelDesc,
        report.fetched(),
        report.published(),
        report.retried(),
        report.failed(),
        report.leaseMissed());

    XxlJobHelper.handleSuccess(
        String.format(
            "Relay 完成 channel=%s fetched=%d published=%d retried=%d failed=%d leaseMissed=%d",
            channelDesc,
            report.fetched(),
            report.published(),
            report.retried(),
            report.failed(),
            report.leaseMissed()));
  }

  /// 处理 relay 失败,包含错误日志和报告。
  private void handleRelayFailure(Exception ex) {
    log.error("outbox relay 任务执行失败: {}", ex.getMessage(), ex);
    XxlJobHelper.handleFail("Relay 失败: " + ex.getMessage());
  }

  /// 构建 relay 命令: 目标通道、时间基准、批量大小、租约配置和重试策略。
  ///
  /// @param param 任务参数(字段可能为 null)
  /// @param now 当前时间(从注入的 Clock 获取以支持可测试性)
  /// @return relay 命令
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

  /// 解析通道;null/空白时回退到默认配置的通道。
  private ChannelKey resolveChannel(String channel) {
    if (CharSequenceUtil.isBlank(channel)) {
      return null; // 让构建器回退到其默认值
    }
    String trimmed = CharSequenceUtil.trim(channel);
    var byChannel = IngestPublishingChannels.fromChannel(trimmed);
    if (byChannel.isPresent()) {
      return byChannel.get();
    }
    try {
      return IngestPublishingChannels.valueOf(trimmed.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IngestScheduleParameterException("非法的通道值: " + channel, ex);
    }
  }

  /// 解析持续时间字符串: 支持 ISO-8601 格式(以 PT 开头)或纯数字秒数字符串。
  ///
  /// @param value 持续时间字符串
  /// @return Duration 或空白时为 null
  /// @throws IngestScheduleParameterException 当格式非法时
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
      throw new IngestScheduleParameterException("非法的持续时间值: " + value, ex);
    }
  }

  /// 解析 JSON 参数;失败时抛出调度参数异常。
  private OutboxRelayJobParam parseParam(String param) {
    if (CharSequenceUtil.isBlank(param)) {
      return new OutboxRelayJobParam(null, null, null, null, null);
    }
    try {
      return objectMapper.readValue(param, OutboxRelayJobParam.class);
    } catch (Exception ex) {
      throw new IngestScheduleParameterException("relay 参数解析失败: " + ex.getMessage(), ex);
    }
  }

  /// 构建租约所有者 ID: host + jobId + threadId + uuid,以避免冲突并帮助追踪。
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
