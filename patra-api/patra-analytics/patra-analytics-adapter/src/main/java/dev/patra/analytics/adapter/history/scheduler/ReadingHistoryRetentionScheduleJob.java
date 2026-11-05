package dev.patra.analytics.adapter.history.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.analytics.app.history.usecase.purge.PurgeExpiredHistoryCommand;
import dev.patra.analytics.app.history.usecase.purge.PurgeResult;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 阅读历史过期清理的 XXL-Job 入口（handler `readingHistoryRetentionJob`），无任务参数：删 12 个月前的历史与 2 天前的
/// 删除记录（阅读历史 spec §7.1）。
///
/// mini 的 xxl-job-admin 里在执行器 `patra-analytics` 下每天 06:40 调一次（Cron `0 40 6 * * ?`，错开注销清理的
/// 06:20）。失败回写失败（调度中心标红），删除幂等，次日自动重跑。
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadingHistoryRetentionScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("readingHistoryRetentionJob")
  public void execute() {
    log.info("阅读历史过期清理任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      PurgeResult result = commandBus.handle(PurgeExpiredHistoryCommand.of());
      XxlJobHelper.handleSuccess(
          "已删除 %d 条历史、%d 条删除记录".formatted(result.historyRows(), result.deletionRows()));
    } catch (RuntimeException ex) {
      log.error("阅读历史过期清理任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
