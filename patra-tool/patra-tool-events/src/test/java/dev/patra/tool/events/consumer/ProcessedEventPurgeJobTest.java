package dev.patra.tool.events.consumer;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@DisplayName("已处理事件清理的 XXL-Job 任务")
class ProcessedEventPurgeJobTest {

  private final ProcessedEventPurger purger = mock(ProcessedEventPurger.class);

  private final ProcessedEventPurgeJob job = new ProcessedEventPurgeJob(purger);

  @Test
  @DisplayName("成功时回写删除条数")
  void should_report_count_on_success() {
    when(purger.purge()).thenReturn(42);
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      job.execute();

      xxl.verify(() -> XxlJobHelper.handleSuccess("删除 42 条已处理事件"));
    }
  }

  @Test
  @DisplayName("出错时回写失败原因")
  void should_report_failure_message() {
    when(purger.purge()).thenThrow(new IllegalStateException("boom"));
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      job.execute();

      xxl.verify(() -> XxlJobHelper.handleFail(contains("boom")));
    }
  }
}
