package dev.patra.identity.adapter.account.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.identity.app.account.usecase.deletion.ReminderRunResult;
import dev.patra.identity.app.account.usecase.deletion.SendAccountDeletionRemindersCommand;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 注销到期前提醒的 XXL-Job 入口（handler `accountDeletionReminderJob`），无任务参数。
///
/// mini 的 xxl-job-admin 里每小时第 15 分调一次（Cron `0 15 * * * ?`）。有用户失败时回写失败，
/// 失败的提醒标记已退回，下一次运行重发。
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionReminderScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("accountDeletionReminderJob")
  public void execute() {
    log.info("注销到期前提醒任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      ReminderRunResult result = commandBus.handle(SendAccountDeletionRemindersCommand.of());
      if (result.failed() > 0) {
        XxlJobHelper.handleFail("已提醒 " + result.sent() + " 个，失败 " + result.failed() + " 个");
      } else {
        XxlJobHelper.handleSuccess("已提醒 " + result.sent() + " 个");
      }
    } catch (RuntimeException ex) {
      log.error("注销到期前提醒任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
