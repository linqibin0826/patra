package dev.patra.analytics.adapter.history.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.analytics.app.history.usecase.purge.PurgeExpiredHistoryCommand;
import dev.patra.analytics.app.history.usecase.purge.PurgeResult;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 阅读历史过期清理任务：成功回写两张表的删除行数，命令抛异常回写失败、不往外抛。
class ReadingHistoryRetentionScheduleJobTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final ReadingHistoryRetentionScheduleJob job =
      new ReadingHistoryRetentionScheduleJob(commandBus);
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
    when(commandBus.handle(any(PurgeExpiredHistoryCommand.class)))
        .thenReturn(PurgeResult.of(20_042, 7));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    assertThat(context.getHandleMsg()).isEqualTo("已删除 20042 条历史、7 条删除记录");
  }

  @Test
  void should_report_failure_on_exception() {
    when(commandBus.handle(any(PurgeExpiredHistoryCommand.class)))
        .thenThrow(new IllegalStateException("boom"));

    assertThatCode(job::execute).doesNotThrowAnyException();
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).isEqualTo("执行失败：boom");
  }
}
