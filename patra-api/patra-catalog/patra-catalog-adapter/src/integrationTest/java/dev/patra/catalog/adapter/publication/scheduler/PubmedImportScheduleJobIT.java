package dev.patra.catalog.adapter.publication.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobHelper;
import dev.patra.catalog.adapter.publication.config.PubmedDataSourceAutoConfiguration;
import dev.patra.catalog.app.publication.usecase.sync.PubmedFileImportCommand;
import dev.patra.catalog.app.publication.usecase.sync.PubmedFileImportResult;
import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// `PubmedImportScheduleJob` 切片测试。
@SpringBootTest(
    classes = PubmedImportScheduleJob.class,
    properties = {
      "patra.catalog.pubmed.update-url=https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/",
      "patra.catalog.pubmed.baseline-url=https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/",
      "patra.catalog.pubmed.update-start-seq=1335"
    })
@Import(PubmedDataSourceAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("PubmedImportScheduleJob 切片测试")
class PubmedImportScheduleJobIT {

  @Autowired private PubmedImportScheduleJob job;

  @MockitoBean private CommandBus commandBus;

  @Test
  @DisplayName("没给参数：每日增量（UPDATE、续跑、到最新），范围取配置的默认 EXISTING_ONLY，成功时 handleSuccess")
  void should_fill_command_from_properties_and_report_success() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn("");
      ArgumentCaptor<PubmedFileImportCommand> captor =
          ArgumentCaptor.forClass(PubmedFileImportCommand.class);
      when(commandBus.handle(captor.capture()))
          .thenReturn(PubmedFileImportResult.of(2, 1337, PubmedFileCounts.empty(), false));

      job.execute();

      PubmedFileImportCommand command = captor.getValue();
      assertThat(command.kind()).isEqualTo(PubmedFileKind.UPDATE);
      assertThat(command.baseUrl()).isEqualTo("https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/");
      assertThat(command.filePrefix()).isEqualTo("pubmed26n");
      assertThat(command.startSeq()).isEqualTo(1335);
      assertThat(command.scope()).isEqualTo(PubmedImportScope.EXISTING_ONLY);
      assertThat(command.from()).isNull();
      assertThat(command.to()).isNull();
      assertThat(command.maxFiles()).isNull();
      assertThat(command.reprocess()).isFalse();
      xxl.verify(() -> XxlJobHelper.handleSuccess(contains("处理 2 个文件")));
    }
  }

  @Test
  @DisplayName("BASELINE 重处理：目录取 baseline，from / to / scope / reprocess 照传")
  void should_parse_baseline_reprocess_params() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam)
          .thenReturn("kind=BASELINE,from=1333, scope=EXISTING_ONLY,reprocess=true");
      ArgumentCaptor<PubmedFileImportCommand> captor =
          ArgumentCaptor.forClass(PubmedFileImportCommand.class);
      when(commandBus.handle(captor.capture()))
          .thenReturn(PubmedFileImportResult.of(1, 1333, PubmedFileCounts.empty(), false));

      job.execute();

      PubmedFileImportCommand command = captor.getValue();
      assertThat(command.kind()).isEqualTo(PubmedFileKind.BASELINE);
      assertThat(command.baseUrl()).isEqualTo("https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/");
      assertThat(command.from()).isEqualTo(1333);
      assertThat(command.to()).isNull();
      assertThat(command.scope()).isEqualTo(PubmedImportScope.EXISTING_ONLY);
      assertThat(command.reprocess()).isTrue();
    }
  }

  @Test
  @DisplayName("UPDATE 指定区间与上限")
  void should_parse_update_range_and_max_files() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn("kind=UPDATE,from=1335,to=1379,maxFiles=10");
      ArgumentCaptor<PubmedFileImportCommand> captor =
          ArgumentCaptor.forClass(PubmedFileImportCommand.class);
      when(commandBus.handle(captor.capture()))
          .thenReturn(PubmedFileImportResult.of(10, 1344, PubmedFileCounts.empty(), false));

      job.execute();

      assertThat(captor.getValue().from()).isEqualTo(1335);
      assertThat(captor.getValue().to()).isEqualTo(1379);
      assertThat(captor.getValue().maxFiles()).isEqualTo(10);
    }
  }

  @Test
  @DisplayName("BASELINE 缺 from、reprocess 不是 true/false：参数错误，不执行")
  void should_fail_on_invalid_combinations() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn("kind=BASELINE");
      job.execute();
      xxl.when(XxlJobHelper::getJobParam).thenReturn("reprocess=yes");
      job.execute();

      verify(commandBus, never()).handle(any());
      xxl.verify(() -> XxlJobHelper.handleFail(contains("参数错误")), times(2));
    }
  }

  @Test
  @DisplayName("未知或重复的参数报参数错误，不执行")
  void should_fail_on_unknown_or_duplicate_param() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn("from=1,from=2");
      job.execute();
      xxl.when(XxlJobHelper::getJobParam).thenReturn("foo=1");
      job.execute();

      verify(commandBus, never()).handle(any());
      xxl.verify(() -> XxlJobHelper.handleFail(contains("参数错误")), times(2));
    }
  }

  @Test
  @DisplayName("执行失败时 handleFail")
  void should_report_fail_when_sync_throws() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn(null);
      when(commandBus.handle(any(PubmedFileImportCommand.class)))
          .thenThrow(new IllegalStateException("boom"));

      job.execute();

      xxl.verify(() -> XxlJobHelper.handleFail(contains("boom")));
    }
  }

  @Test
  @DisplayName("另一个实例在运行时也算成功，并在消息里写明")
  void should_report_success_when_skipped_by_lock() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn(null);
      when(commandBus.handle(any(PubmedFileImportCommand.class)))
          .thenReturn(PubmedFileImportResult.ofSkippedByLock());

      job.execute();

      xxl.verify(() -> XxlJobHelper.handleSuccess(contains("跳过")));
    }
  }
}
