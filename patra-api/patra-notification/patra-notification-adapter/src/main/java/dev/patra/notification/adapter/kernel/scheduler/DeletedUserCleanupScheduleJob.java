package dev.patra.notification.adapter.kernel.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.notification.app.kernel.usecase.cleanup.CleanupDeletedUsersCommand;
import dev.patra.notification.app.kernel.usecase.cleanup.CleanupResult;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 注销清理的 XXL-Job 入口（handler `deletedUserCleanupJob`），无任务参数。清理的是各子域登记的个人数据
/// （`UserDataCleanupGateway`：preference 的偏好与退订令牌，以后 inbox 的提醒）。写法同 interaction 的同名任务。
///
/// mini 的 xxl-job-admin 里在执行器 `patra-notification` 下每天 06:30 调一次（Cron `0 30 6 * * ?`，错开 interaction
/// 的 06:00；超时 600 秒、不重试）。有批次因 identity 不可用被跳过时回写失败（调度中心标红），次日自动重跑。
@Slf4j
@Component
@RequiredArgsConstructor
public class DeletedUserCleanupScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("deletedUserCleanupJob")
  public void execute() {
    log.info("注销清理任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      CleanupResult result = commandBus.handle(CleanupDeletedUsersCommand.of());
      if (result.failedBatches() > 0) {
        XxlJobHelper.handleFail(
            "已核对 " + result.scannedUsers() + " 人，失败 " + result.failedBatches() + " 批");
      } else {
        XxlJobHelper.handleSuccess(
            "已核对 %d 人：清理 %d 人，删除 %d 行"
                .formatted(result.scannedUsers(), result.deletedUsers(), result.deletedRows()));
      }
    } catch (RuntimeException ex) {
      log.error("注销清理任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
