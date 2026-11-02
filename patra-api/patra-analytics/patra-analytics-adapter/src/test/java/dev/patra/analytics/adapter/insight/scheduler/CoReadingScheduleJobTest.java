package dev.patra.analytics.adapter.insight.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.analytics.app.insight.usecase.coread.RefreshCoReadingCommand;
import dev.patra.analytics.app.insight.usecase.coread.RefreshCoReadingResult;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 共现任务：成功回写结果，命令抛异常回写失败、不往外抛。
class CoReadingScheduleJobTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final CoReadingScheduleJob job = new CoReadingScheduleJob(commandBus);
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
    when(commandBus.handle(any(RefreshCoReadingCommand.class)))
        .thenReturn(RefreshCoReadingResult.of(120, 2100));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    assertThat(context.getHandleMsg()).isEqualTo("120 篇文献、2100 条推荐");
  }

  @Test
  void should_report_failure_on_exception() {
    when(commandBus.handle(any(RefreshCoReadingCommand.class)))
        .thenThrow(new IllegalStateException("boom"));

    assertThatCode(job::execute).doesNotThrowAnyException();
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).isEqualTo("执行失败：boom");
  }
}
