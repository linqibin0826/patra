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
/// **任务参数**：{@link VenueEnrichJobParam}（格式：`targetYear[,minCitedByCount]`）
///
/// **运行建议**：
///
/// - Scopus API 限速 2-3 次/秒，Runner 自动在两次 venue 处理间等待 400ms
/// - 全量富化约需数小时（视期刊数量）
/// - 失败 venue 会在下次 Job 调度时自动被候选查询重新捞起（跨 Job 自愈）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueScopusEnrichScheduleJob {

  private final CommandBus commandBus;

  /// 执行 Scopus 期刊指标富化任务。
  @XxlJob("venueScopusEnrichJob")
  public void executeVenueScopusEnrich() {
    log.info("Scopus 期刊指标富化任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    VenueEnrichJobParam param = VenueEnrichJobParam.fromXxlJobParam();

    try {
      VenueScopusEnrichResult result =
          commandBus.handle(
              new VenueScopusEnrichCommand(param.targetYear(), param.minCitedByCount()));

      String message =
          String.format(
              "Scopus 期刊指标富化已完成: targetYear=%d, minCitedByCount=%d, total=%d, processed=%d, skipped=%d, failed=%d",
              param.targetYear(),
              param.minCitedByCount(),
              result.totalRead(),
              result.processed(),
              result.skipped(),
              result.failed());
      log.info(message);
      XxlJobHelper.handleSuccess(message);

    } catch (Exception ex) {
      log.error("Scopus 期刊指标富化任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
