package dev.patra.notification.adapter.inbox.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.notification.app.inbox.usecase.purge.PurgeExpiredInboxCommand;
import dev.patra.notification.app.inbox.usecase.purge.PurgeResult;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 信箱保留期清理的 XXL-Job 入口（handler `inboxRetentionJob`），无任务参数：删除北京时间 5 个自然月前的提醒、
/// 没有提醒引用的评论快照与经验墓碑。
///
/// mini 的 xxl-job-admin 里在执行器 `patra-notification` 下每天 06:45 调一次（Cron `0 45 6 * * ?`，错开 06:30 的注销
/// 清理；超时 300 秒、不重试）；失败次日自动重跑。
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxRetentionScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("inboxRetentionJob")
  public void execute() {
    log.info("信箱保留期清理任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      PurgeResult result = commandBus.handle(PurgeExpiredInboxCommand.of());
      XxlJobHelper.handleSuccess(
          "已删除 %d 条提醒、%d 条评论快照、%d 条经验墓碑"
              .formatted(result.notifications(), result.comments(), result.tombstones()));
    } catch (RuntimeException ex) {
      log.error("信箱保留期清理任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
