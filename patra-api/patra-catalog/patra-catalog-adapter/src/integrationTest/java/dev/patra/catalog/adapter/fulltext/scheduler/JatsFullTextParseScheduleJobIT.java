package dev.patra.catalog.adapter.fulltext.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobHelper;
import dev.patra.catalog.app.fulltext.usecase.parse.FullTextParseRunStats;
import dev.patra.catalog.app.fulltext.usecase.parse.JatsFullTextParseCommand;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// `JatsFullTextParseScheduleJob` 切片测试。
@SpringBootTest(classes = JatsFullTextParseScheduleJob.class)
@ActiveProfiles("test")
@DisplayName("JatsFullTextParseScheduleJob 切片测试")
class JatsFullTextParseScheduleJobIT {

  @Autowired private JatsFullTextParseScheduleJob job;

  @MockitoBean private CommandBus commandBus;

  @Test
  @DisplayName("成功时 handleSuccess 带统计摘要")
  void should_report_success_with_stats() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      when(commandBus.handle(any(JatsFullTextParseCommand.class)))
          .thenReturn(FullTextParseRunStats.of(4, 3, 1, 0));

      job.execute();

      xxl.verify(() -> XxlJobHelper.handleSuccess(contains("成功 3")));
    }
  }

  @Test
  @DisplayName("执行失败时 handleFail")
  void should_report_fail_when_parse_throws() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      when(commandBus.handle(any(JatsFullTextParseCommand.class)))
          .thenThrow(new IllegalStateException("boom"));

      job.execute();

      xxl.verify(() -> XxlJobHelper.handleFail(contains("boom")));
    }
  }
}
