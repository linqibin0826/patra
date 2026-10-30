package dev.patra.tool.events.resubmit;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@DisplayName("事件补投的 XXL-Job 任务")
class EventPublicationResubmitJobTest {

  private final EventPublicationResubmitter resubmitter = mock(EventPublicationResubmitter.class);

  private final EventPublicationResubmitJob job = new EventPublicationResubmitJob(resubmitter);

  @Test
  @DisplayName("成功时回写补投条数")
  void should_report_count_on_success() {
    when(resubmitter.resubmit()).thenReturn(2);
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      job.execute();

      xxl.verify(() -> XxlJobHelper.handleSuccess("补投 2 条"));
    }
  }

  @Test
  @DisplayName("出错时回写失败原因")
  void should_report_failure_message() {
    when(resubmitter.resubmit()).thenThrow(new IllegalStateException("boom"));
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      job.execute();

      xxl.verify(() -> XxlJobHelper.handleFail(contains("boom")));
    }
  }
}
