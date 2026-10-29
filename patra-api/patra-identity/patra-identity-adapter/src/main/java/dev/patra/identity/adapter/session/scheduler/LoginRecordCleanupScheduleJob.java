package dev.patra.identity.adapter.session.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.identity.app.session.usecase.purge.PurgeRetiredLoginRecordsCommand;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/// 登录记录清理的 XXL-Job 入口（handler `loginRecordCleanupJob`），无任务参数（PAP-99 设计第 14 节）。
///
/// 保留期从 `patra.identity.session.record-retention` 读（取值只写在 identity 的 `application.yml`，190 天，
/// 《网络安全法》日志留存 ≥ 6 个月；缺这个配置启动失败），放进命令：app 层不依赖 boot 的配置类。mini 的 xxl-job-admin 里每天 04:40 调一次（令牌清理
// 04:30 之后）。
@Slf4j
@Component
public class LoginRecordCleanupScheduleJob {

  private final CommandBus commandBus;
  private final Duration retention;

  /// 创建任务。
  ///
  /// @param commandBus 命令总线
  /// @param retention 登录记录结束或过期之后的保留期
  public LoginRecordCleanupScheduleJob(
      CommandBus commandBus,
      @Value("${patra.identity.session.record-retention}") Duration retention) {
    this.commandBus = commandBus;
    this.retention = retention;
  }

  /// 执行一次清理。
  @XxlJob("loginRecordCleanupJob")
  public void execute() {
    log.info("登录记录清理任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      int deleted = commandBus.handle(PurgeRetiredLoginRecordsCommand.of(retention));
      String message = "已删除登录记录 " + deleted + " 行";
      log.info(message);
      XxlJobHelper.handleSuccess(message);
    } catch (RuntimeException ex) {
      log.error("登录记录清理任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
