package com.patra.catalog.adapter.scheduler.job;

import com.patra.catalog.app.usecase.venue.scopus.command.VenueScopusEnrichCommand;
import com.patra.catalog.app.usecase.venue.scopus.command.VenueScopusEnrichResult;
import com.patra.common.cqrs.CommandBus;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// Scopus 期刊指标富化定时任务。
///
/// 通过 XXL-Job 控制台手动触发，启动 Scopus 期刊指标的批量富化 Job。
///
/// **JobHandler 名称**: `venueScopusEnrichJob`
///
/// **无需参数**：处理范围由 Spring Batch Reader 的 JPQL 条件
/// （`NOT EXISTS (SELECT 1 FROM ScopusRatingEntity ...)`）自动确定，天然支持断点续传。
///
/// **运行建议**：
///
/// - Scopus API 限速 2-3 次/秒，每条约 400ms
/// - 全量富化约需数小时（视期刊数量）
/// - 可随时中断，重启后自动跳过已处理记录
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueScopusEnrichScheduleJob {

  private final CommandBus commandBus;

  /// 执行 Scopus 期刊指标富化任务。
  ///
  /// **JobHandler 名称**: `venueScopusEnrichJob`
  @XxlJob("venueScopusEnrichJob")
  public void executeVenueScopusEnrich() {
    log.info("Scopus 期刊指标富化任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      VenueScopusEnrichResult result = commandBus.handle(new VenueScopusEnrichCommand());

      String message = String.format("Scopus 期刊指标富化 Job 已启动，executionId=%d", result.executionId());
      log.info(message);
      XxlJobHelper.handleSuccess(message);

    } catch (Exception ex) {
      log.error("Scopus 期刊指标富化任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
