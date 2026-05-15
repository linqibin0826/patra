package com.patra.catalog.adapter.scheduler.job;

import com.patra.catalog.app.usecase.venue.VenueEnrichRunStats;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichCommand;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.linqibin.commons.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// LetPub 期刊富化定时任务。
///
/// 通过 XXL-Job 控制台手动触发，启动 LetPub 期刊评价数据的循环式富化任务
/// （worker loop + 游标分页，由 App 层 Runner/Worker/Persister 三段结构驱动）。
///
/// **JobHandler 名称**: `venueLetPubEnrichJob`
///
/// **任务参数**：{@link VenueEnrichJobParam}（格式：`targetYear[,minCitedByCount]`）
///
/// **运行建议**：
///
/// - 全量富化约需 70 小时（30,000 条期刊，每条 8-10 秒含反爬延迟）
/// - 失败 venue 会在下次 Job 调度时自动被候选查询重新捞起（跨 Job 自愈）
/// - 建议在低峰期手动触发
/// - 超时时间建议设置为 0（无限制）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueLetPubEnrichScheduleJob {

  private final CommandBus commandBus;

  /// 执行 LetPub 期刊富化任务。
  @XxlJob("venueLetPubEnrichJob")
  public void executeVenueLetPubEnrich() {
    log.info("LetPub 期刊富化任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    VenueEnrichJobParam param = VenueEnrichJobParam.fromXxlJobParam();

    try {
      VenueEnrichRunStats result =
          commandBus.handle(
              new VenueLetPubEnrichCommand(param.targetYear(), param.minCitedByCount()));

      String message =
          String.format(
              "LetPub 期刊富化已完成: targetYear=%d, minCitedByCount=%d,"
                  + " total=%d, processed=%d, skipped=%d, failed=%d",
              param.targetYear(),
              param.minCitedByCount(),
              result.totalRead(),
              result.processed(),
              result.skipped(),
              result.failed());
      log.info(message);
      XxlJobHelper.handleSuccess(message);

    } catch (Exception ex) {
      log.error("LetPub 期刊富化任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
