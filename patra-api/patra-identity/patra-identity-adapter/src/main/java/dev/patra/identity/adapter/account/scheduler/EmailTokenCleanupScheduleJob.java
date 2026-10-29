package dev.patra.identity.adapter.account.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.identity.app.account.usecase.email.PurgeExpiredEmailTokensCommand;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/// 过期邮件令牌清理的 XXL-Job 入口（handler `emailTokenCleanupJob`），无任务参数。
///
/// 保留期从 `patra.identity.mail.token-retention` 读（默认 30 天），放进命令：app 层不依赖 boot 的配置类。
/// mini 的 xxl-job-admin 里每天 04:30 调一次。
@Slf4j
@Component
public class EmailTokenCleanupScheduleJob {

  private final CommandBus commandBus;
  private final Duration retention;

  /// 创建任务。
  ///
  /// @param commandBus 命令总线
  /// @param retention 过期令牌的保留期
  public EmailTokenCleanupScheduleJob(
      CommandBus commandBus,
      @Value("${patra.identity.mail.token-retention:30d}") Duration retention) {
    this.commandBus = commandBus;
    this.retention = retention;
  }

  /// 执行一次清理。
  @XxlJob("emailTokenCleanupJob")
  public void execute() {
    log.info("过期邮件令牌清理任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      int deleted = commandBus.handle(PurgeExpiredEmailTokensCommand.of(retention));
      String message = "已删除过期令牌 " + deleted + " 行";
      log.info(message);
      XxlJobHelper.handleSuccess(message);
    } catch (RuntimeException ex) {
      log.error("过期邮件令牌清理任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
