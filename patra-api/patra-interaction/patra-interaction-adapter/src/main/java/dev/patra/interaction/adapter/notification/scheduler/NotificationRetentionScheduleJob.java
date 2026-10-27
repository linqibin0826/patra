package dev.patra.interaction.adapter.notification.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.interaction.app.notification.usecase.purge.PurgeExpiredNotificationsCommand;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 提醒保留期清理的 XXL-Job 入口（handler `notificationRetentionJob`），无任务参数：删除 5 个月前的提醒。
///
/// mini 的 xxl-job-admin 里每天 06:45 调一次（Cron `0 45 6 * * ?`，超时 300 秒、不重试）；失败次日自动重跑。
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetentionScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("notificationRetentionJob")
  public void execute() {
    log.info("提醒清理任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      int deleted = commandBus.handle(PurgeExpiredNotificationsCommand.of());
      XxlJobHelper.handleSuccess("已删除 %d 条 5 个月前的提醒".formatted(deleted));
    } catch (RuntimeException ex) {
      log.error("提醒清理任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
