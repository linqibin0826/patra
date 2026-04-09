package com.patra.catalog.adapter.scheduler.job;

import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichCommand;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichResult;
import com.patra.common.cqrs.CommandBus;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// LetPub 期刊富化定时任务。
///
/// 通过 XXL-Job 控制台手动触发，启动 LetPub 期刊评价数据的批量富化 Job。
///
/// **JobHandler 名称**: `venueLetPubEnrichJob`
///
/// **任务参数**：{@link VenueEnrichJobParam}（格式：`targetYear[,minCitedByCount]`）
///
/// **运行建议**：
///
/// - 全量富化约需 70 小时（30,000 条期刊，每条 8-10 秒含反爬延迟）
/// - 可随时中断，重启后自动跳过已处理记录
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
      VenueLetPubEnrichResult result =
          commandBus.handle(
              new VenueLetPubEnrichCommand(param.targetYear(), param.minCitedByCount()));

      String message =
          String.format(
              "LetPub 期刊富化 Job 已启动，targetYear=%d, minCitedByCount=%d, executionId=%d",
              param.targetYear(), param.minCitedByCount(), result.executionId());
      log.info(message);
      XxlJobHelper.handleSuccess(message);

    } catch (Exception ex) {
      log.error("LetPub 期刊富化任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
