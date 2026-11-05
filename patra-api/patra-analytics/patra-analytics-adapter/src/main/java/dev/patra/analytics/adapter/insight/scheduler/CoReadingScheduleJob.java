package dev.patra.analytics.adapter.insight.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.analytics.app.insight.usecase.coread.RefreshCoReadingCommand;
import dev.patra.analytics.app.insight.usecase.coread.RefreshCoReadingResult;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 共现（看过这篇的人还看了）重算的 XXL-Job 入口（handler `coReadingJob`），无任务参数（insight spec §6.2）。
///
/// mini 的 xxl-job-admin 里在执行器 `patra-analytics` 下每天 04:30 调一次（Cron `0 30 4 * * ?`，错开 06:20 注销清理、
/// 06:40 历史清理）。失败回写失败（调度中心标红），旧快照保留；整表替换是幂等的，可以手动重跑。
@Slf4j
@Component
@RequiredArgsConstructor
public class CoReadingScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("coReadingJob")
  public void execute() {
    log.info("共现重算任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      RefreshCoReadingResult result = commandBus.handle(RefreshCoReadingCommand.of());
      XxlJobHelper.handleSuccess("%d 篇文献、%d 条推荐".formatted(result.articles(), result.rows()));
    } catch (RuntimeException ex) {
      log.error("共现重算任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
