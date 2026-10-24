package dev.patra.interaction.adapter.subject.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.interaction.app.subject.usecase.reconcile.ReconcileSubjectsCommand;
import dev.patra.interaction.app.subject.usecase.reconcile.ReconciliationResult;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 对象对账任务：没有失败批次回写成功与计数，有失败批次或命令抛异常回写失败，都不往外抛。
class SubjectReconciliationScheduleJobTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final SubjectReconciliationScheduleJob job =
      new SubjectReconciliationScheduleJob(commandBus);
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
    when(commandBus.handle(any(ReconcileSubjectsCommand.class))).thenReturn(result(0));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    assertThat(context.getHandleMsg()).isEqualTo("已核对 103 个：换 ID 1、失效 2、复活 3");
  }

  @Test
  void should_report_failure_when_batches_failed() {
    when(commandBus.handle(any(ReconcileSubjectsCommand.class))).thenReturn(result(1));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).isEqualTo("已核对 103 个，失败 1 批");
  }

  @Test
  void should_report_failure_on_exception() {
    when(commandBus.handle(any(ReconcileSubjectsCommand.class)))
        .thenThrow(new IllegalStateException("boom"));

    assertThatCode(job::execute).doesNotThrowAnyException();
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).isEqualTo("执行失败：boom");
  }

  /// 固定计数的结果。
  ///
  /// @param failedBatches 失败批次
  /// @return 结果
  private static ReconciliationResult result(int failedBatches) {
    return ReconciliationResult.builder()
        .scanned(103)
        .sourceIdChanged(1)
        .becameUnavailable(2)
        .revived(3)
        .failedBatches(failedBatches)
        .build();
  }
}
