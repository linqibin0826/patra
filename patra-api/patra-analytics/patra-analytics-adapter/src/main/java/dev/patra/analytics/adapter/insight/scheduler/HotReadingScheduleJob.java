package dev.patra.analytics.adapter.insight.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.analytics.app.insight.usecase.hot.RefreshHotReadingCommand;
import dev.patra.analytics.app.insight.usecase.hot.RefreshHotReadingResult;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 热读重算的 XXL-Job 入口（handler `hotReadingJob`），无任务参数（insight spec §5.3）。
///
/// mini 的 xxl-job-admin 里在执行器 `patra-analytics` 下每小时第 5 分钟调一次（Cron `0 5 * * * ?`）。失败回写失败
/// （调度中心标红），旧快照保留；整表替换是幂等的，可以手动重跑。
@Slf4j
@Component
@RequiredArgsConstructor
public class HotReadingScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("hotReadingJob")
  public void execute() {
    log.info("热读重算任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      RefreshHotReadingResult result = commandBus.handle(RefreshHotReadingCommand.of());
      XxlJobHelper.handleSuccess("上榜 %d 篇、补位 %d 篇".formatted(result.ranked(), result.backfill()));
    } catch (RuntimeException ex) {
      log.error("热读重算任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
