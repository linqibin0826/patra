package dev.patra.identity.adapter.account.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.identity.app.account.usecase.deletion.DeletionRunResult;
import dev.patra.identity.app.account.usecase.deletion.ExecuteDueAccountDeletionsCommand;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 注销到期执行的 XXL-Job 入口（handler `accountDeletionJob`），无任务参数。
///
/// mini 的 xxl-job-admin 里每小时第 5 分调一次（Cron `0 5 * * * ?`）。有用户失败时回写失败（调度中心标红），
/// 失败的用户仍在冷静期，下一次运行重试。
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("accountDeletionJob")
  public void execute() {
    log.info("注销到期执行任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      DeletionRunResult result = commandBus.handle(ExecuteDueAccountDeletionsCommand.of());
      if (result.failed() > 0) {
        XxlJobHelper.handleFail("已注销 " + result.completed() + " 个，失败 " + result.failed() + " 个");
      } else {
        XxlJobHelper.handleSuccess("已注销 " + result.completed() + " 个");
      }
    } catch (RuntimeException ex) {
      log.error("注销到期执行任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
