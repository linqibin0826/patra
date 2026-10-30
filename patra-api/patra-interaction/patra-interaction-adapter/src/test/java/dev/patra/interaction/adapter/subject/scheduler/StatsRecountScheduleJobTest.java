package dev.patra.interaction.adapter.subject.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.interaction.app.subject.usecase.recount.CounterResult;
import dev.patra.interaction.app.subject.usecase.recount.RecountResult;
import dev.patra.interaction.app.subject.usecase.recount.RecountStatsCommand;
import dev.patra.tool.cqrs.CommandBus;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 统计重算任务：回写差异条数与各项明细（有差异也算成功），命令抛异常回写失败，都不往外抛。
class StatsRecountScheduleJobTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final StatsRecountScheduleJob job = new StatsRecountScheduleJob(commandBus);
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
    when(commandBus.handle(any(RecountStatsCommand.class)))
        .thenReturn(
            RecountResult.of(
                List.of(
                    CounterResult.of("collection.collector_count", 2),
                    CounterResult.of("reaction.helpful_count", 0))));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    assertThat(context.getHandleMsg())
        .isEqualTo("差异 2 行：{collection.collector_count=2, reaction.helpful_count=0}");
  }

  @Test
  void should_report_failure_on_exception() {
    when(commandBus.handle(any(RecountStatsCommand.class)))
        .thenThrow(new IllegalStateException("boom"));

    assertThatCode(job::execute).doesNotThrowAnyException();
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).isEqualTo("执行失败：boom");
  }
}
