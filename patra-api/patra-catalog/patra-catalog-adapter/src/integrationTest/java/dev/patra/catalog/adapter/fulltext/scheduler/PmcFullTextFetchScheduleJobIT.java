package dev.patra.catalog.adapter.fulltext.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobHelper;
import dev.patra.catalog.app.fulltext.usecase.fetch.FullTextFetchRunStats;
import dev.patra.catalog.app.fulltext.usecase.fetch.PmcFullTextFetchCommand;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// `PmcFullTextFetchScheduleJob` 切片测试。
@SpringBootTest(classes = PmcFullTextFetchScheduleJob.class)
@ActiveProfiles("test")
@DisplayName("PmcFullTextFetchScheduleJob 切片测试")
class PmcFullTextFetchScheduleJobIT {

  @Autowired private PmcFullTextFetchScheduleJob job;

  @MockitoBean private CommandBus commandBus;

  @Test
  @DisplayName("没给参数时处理全部到期候选，成功时 handleSuccess")
  void should_run_all_candidates_without_params() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn(" ");
      ArgumentCaptor<PmcFullTextFetchCommand> captor =
          ArgumentCaptor.forClass(PmcFullTextFetchCommand.class);
      when(commandBus.handle(captor.capture())).thenReturn(FullTextFetchRunStats.of(3, 2, 1, 0));

      job.execute();

      assertThat(captor.getValue().publicationIds()).isEmpty();
      xxl.verify(() -> XxlJobHelper.handleSuccess(contains("处理 3 篇")));
    }
  }

  @Test
  @DisplayName("publicationIds 整段按逗号切成 ID 列表")
  void should_parse_publication_ids() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn("publicationIds=1, 2,3");
      ArgumentCaptor<PmcFullTextFetchCommand> captor =
          ArgumentCaptor.forClass(PmcFullTextFetchCommand.class);
      when(commandBus.handle(captor.capture())).thenReturn(FullTextFetchRunStats.empty());

      job.execute();

      assertThat(captor.getValue().publicationIds()).containsExactly(1L, 2L, 3L);
    }
  }

  @Test
  @DisplayName("参数格式不对时报参数错误，不执行")
  void should_fail_on_invalid_params() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn("ids=1");
      job.execute();

      verify(commandBus, never()).handle(any());
      xxl.verify(() -> XxlJobHelper.handleFail(contains("参数错误")));
    }
  }

  @Test
  @DisplayName("执行失败时 handleFail")
  void should_report_fail_when_fetch_throws() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn(null);
      when(commandBus.handle(any(PmcFullTextFetchCommand.class)))
          .thenThrow(new IllegalStateException("boom"));

      job.execute();

      xxl.verify(() -> XxlJobHelper.handleFail(contains("boom")));
    }
  }
}
