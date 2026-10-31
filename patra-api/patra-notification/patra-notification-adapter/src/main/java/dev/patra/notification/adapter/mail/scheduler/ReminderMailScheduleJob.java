package dev.patra.notification.adapter.mail.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.notification.app.mail.usecase.flush.FlushDueRemindersCommand;
import dev.patra.notification.app.mail.usecase.flush.FlushResult;
import dev.patra.notification.app.mail.usecase.send.SendPendingDeliveriesCommand;
import dev.patra.notification.app.mail.usecase.send.SendResult;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 提醒邮件的每分钟任务（handler `reminderMailJob`，无参数，spec 7.2）：先把到点的离站排队行处理掉，再装配并发出到点的投递
/// （含每日汇总的）。
///
/// mini 的调度中心在执行器 `patra-notification` 下每分钟一次（Cron `0 * * * * ?`；路由第一个、阻塞处理「丢弃后续调度」、
/// 超时 300 秒、不重试）。依赖不可用或发信临时故障时本轮提前停下、回写失败（调度中心标红），下一分钟自动再试。
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderMailScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("reminderMailJob")
  public void execute() {
    try {
      FlushResult flushed = commandBus.handle(FlushDueRemindersCommand.of());
      SendResult sent = commandBus.handle(SendPendingDeliveriesCommand.of());
      String summary =
          "到点 %d 人，新建 %d 封；发出 %d 封、跳过 %d 封、推迟 %d 封"
              .formatted(
                  flushed.recipients(),
                  flushed.deliveries(),
                  sent.sent(),
                  sent.skipped(),
                  sent.deferred());
      if (sent.stoppedEarly()) {
        XxlJobHelper.handleFail("依赖不可用或发信临时故障，本轮提前停下：" + summary);
      } else {
        XxlJobHelper.handleSuccess(summary);
      }
    } catch (RuntimeException ex) {
      log.error("提醒邮件任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
