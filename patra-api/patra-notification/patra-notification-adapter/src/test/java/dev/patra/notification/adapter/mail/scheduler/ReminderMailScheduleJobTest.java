package dev.patra.notification.adapter.mail.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.notification.app.mail.usecase.flush.FlushDueRemindersCommand;
import dev.patra.notification.app.mail.usecase.flush.FlushResult;
import dev.patra.notification.app.mail.usecase.send.SendPendingDeliveriesCommand;
import dev.patra.notification.app.mail.usecase.send.SendResult;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// 每分钟的提醒邮件任务：先到点处理、再发送；提前停下回写失败，让调度中心标红。
class ReminderMailScheduleJobTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final ReminderMailScheduleJob job = new ReminderMailScheduleJob(commandBus);
  private final XxlJobContext context = new XxlJobContext(1L, null, null, 0, 1);

  @BeforeEach
  void setContext() {
    XxlJobContext.setXxlJobContext(context);
  }

  @AfterEach
  void clearContext() {
    XxlJobContext.setXxlJobContext(null);
  }

  /// 先到点处理、再发送，成功时写上计数。
  @Test
  void reminder_job_flushes_then_sends() {
    when(commandBus.handle(any(FlushDueRemindersCommand.class)))
        .thenReturn(FlushResult.of(2, 1, 1));
    when(commandBus.handle(any(SendPendingDeliveriesCommand.class)))
        .thenReturn(SendResult.of(1, 0, 0, false));

    job.execute();

    InOrder order = inOrder(commandBus);
    order.verify(commandBus).handle(any(FlushDueRemindersCommand.class));
    order.verify(commandBus).handle(any(SendPendingDeliveriesCommand.class));
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    assertThat(context.getHandleMsg()).isEqualTo("到点 2 人，新建 1 封；发出 1 封、跳过 0 封、推迟 0 封");
  }

  /// 依赖不可用、本轮提前停下：回写失败。
  @Test
  void reminder_job_marks_fail_when_stopped_early() {
    when(commandBus.handle(any(FlushDueRemindersCommand.class)))
        .thenReturn(FlushResult.of(0, 0, 0));
    when(commandBus.handle(any(SendPendingDeliveriesCommand.class)))
        .thenReturn(SendResult.of(0, 0, 1, true));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).startsWith("依赖不可用或发信临时故障，本轮提前停下");
  }

  /// 命令抛异常：回写失败，不往外抛。
  @Test
  void reminder_job_reports_failure_on_exception() {
    when(commandBus.handle(any(FlushDueRemindersCommand.class)))
        .thenThrow(new IllegalStateException("boom"));

    assertThatCode(job::execute).doesNotThrowAnyException();
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).isEqualTo("执行失败：boom");
  }
}
