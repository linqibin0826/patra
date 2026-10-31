package dev.patra.notification.adapter.kernel.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.notification.app.kernel.usecase.cleanup.CleanupDeletedUsersCommand;
import dev.patra.notification.app.kernel.usecase.cleanup.CleanupResult;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 注销清理任务（照 interaction）：没有失败批次回写成功与计数，有失败批次或命令抛异常回写失败，都不往外抛。
class DeletedUserCleanupScheduleJobTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final DeletedUserCleanupScheduleJob job = new DeletedUserCleanupScheduleJob(commandBus);
  private final XxlJobContext context = new XxlJobContext(1L, null, null, 0, 1);

  @BeforeEach
  void setContext() {
    XxlJobContext.setXxlJobContext(context);
  }

  @AfterEach
  void clearContext() {
    XxlJobContext.setXxlJobContext(null);
  }

  /// 没有失败批次：成功，写上计数。
  @Test
  void should_report_success_with_counts() {
    when(commandBus.handle(any(CleanupDeletedUsersCommand.class))).thenReturn(result(0));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    assertThat(context.getHandleMsg()).isEqualTo("已核对 12 人：清理 2 人，删除 41 行");
  }

  /// 有批次因 identity 不可用被跳过：失败，调度中心标红，次日重跑。
  @Test
  void should_report_failure_when_batches_failed() {
    when(commandBus.handle(any(CleanupDeletedUsersCommand.class))).thenReturn(result(1));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).isEqualTo("已核对 12 人，失败 1 批");
  }

  /// 命令抛异常：回写失败，不往外抛。
  @Test
  void should_report_failure_on_exception() {
    when(commandBus.handle(any(CleanupDeletedUsersCommand.class)))
        .thenThrow(new IllegalStateException("boom"));

    assertThatCode(job::execute).doesNotThrowAnyException();
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).isEqualTo("执行失败：boom");
  }

  /// 固定计数的结果。
  ///
  /// @param failedBatches 失败批次
  /// @return 结果
  private static CleanupResult result(int failedBatches) {
    return CleanupResult.of(12, 2, 41, failedBatches);
  }
}
