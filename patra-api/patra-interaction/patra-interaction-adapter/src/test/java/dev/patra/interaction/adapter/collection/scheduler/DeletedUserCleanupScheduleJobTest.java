package dev.patra.interaction.adapter.collection.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.interaction.app.collection.usecase.cleanup.CleanupDeletedUsersCommand;
import dev.patra.interaction.app.collection.usecase.cleanup.CleanupResult;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 注销清理任务：没有失败批次回写成功与计数，有失败批次或命令抛异常回写失败，都不往外抛。
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

  @Test
  void should_report_success_with_counts() {
    when(commandBus.handle(any(CleanupDeletedUsersCommand.class))).thenReturn(result(0));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    assertThat(context.getHandleMsg()).isEqualTo("已核对 12 人：清理 2 人，条目 30、收藏夹 4");
  }

  @Test
  void should_report_failure_when_batches_failed() {
    when(commandBus.handle(any(CleanupDeletedUsersCommand.class))).thenReturn(result(1));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).isEqualTo("已核对 12 人，失败 1 批");
  }

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
    return CleanupResult.builder()
        .scannedUsers(12)
        .deletedUsers(2)
        .libraryItems(30)
        .collections(4)
        .collectionItems(7)
        .failedBatches(failedBatches)
        .build();
  }
}
