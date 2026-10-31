package dev.patra.notification.adapter.inbox.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.notification.app.inbox.usecase.purge.PurgeExpiredInboxCommand;
import dev.patra.notification.app.inbox.usecase.purge.PurgeResult;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 信箱保留期任务：成功回写各删了几行，命令抛异常回写失败，不往外抛。
class InboxRetentionScheduleJobTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final InboxRetentionScheduleJob job = new InboxRetentionScheduleJob(commandBus);
  private final XxlJobContext context = new XxlJobContext(1L, null, null, 0, 1);

  @BeforeEach
  void setContext() {
    XxlJobContext.setXxlJobContext(context);
  }

  @AfterEach
  void clearContext() {
    XxlJobContext.setXxlJobContext(null);
  }

  /// 成功：写上三种各删了几行。
  @Test
  void should_report_success_with_counts() {
    when(commandBus.handle(any(PurgeExpiredInboxCommand.class)))
        .thenReturn(PurgeResult.of(5, 3, 1));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    assertThat(context.getHandleMsg()).isEqualTo("已删除 5 条提醒、3 条评论快照、1 条经验墓碑");
  }

  /// 命令抛异常：回写失败，不往外抛。
  @Test
  void should_report_failure_on_exception() {
    when(commandBus.handle(any(PurgeExpiredInboxCommand.class)))
        .thenThrow(new IllegalStateException("boom"));

    assertThatCode(job::execute).doesNotThrowAnyException();
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).isEqualTo("执行失败：boom");
  }
}
