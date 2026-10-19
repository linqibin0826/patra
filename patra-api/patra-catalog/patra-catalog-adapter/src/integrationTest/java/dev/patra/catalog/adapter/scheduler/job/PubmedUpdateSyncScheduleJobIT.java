package dev.patra.catalog.adapter.scheduler.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobHelper;
import dev.patra.catalog.adapter.scheduler.config.PubmedDataSourceAutoConfiguration;
import dev.patra.catalog.app.usecase.publication.update.command.PubmedUpdateSyncCommand;
import dev.patra.catalog.app.usecase.publication.update.dto.PubmedUpdateSyncResult;
import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileCounts;
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

/// `PubmedUpdateSyncScheduleJob` 切片测试。
@SpringBootTest(
    classes = PubmedUpdateSyncScheduleJob.class,
    properties = {
      "patra.catalog.pubmed.update-url=https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/",
      "patra.catalog.pubmed.update-scope=EXISTING_ONLY",
      "patra.catalog.pubmed.update-start-seq=1335"
    })
@Import(PubmedDataSourceAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("PubmedUpdateSyncScheduleJob 切片测试")
class PubmedUpdateSyncScheduleJobIT {

  @Autowired private PubmedUpdateSyncScheduleJob job;

  @MockitoBean private CommandBus commandBus;

  @Test
  @DisplayName("没给参数时从配置补齐范围、起点与目录，成功时 handleSuccess")
  void should_fill_command_from_properties_and_report_success() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn("");
      ArgumentCaptor<PubmedUpdateSyncCommand> captor =
          ArgumentCaptor.forClass(PubmedUpdateSyncCommand.class);
      when(commandBus.handle(captor.capture()))
          .thenReturn(PubmedUpdateSyncResult.of(2, 1337, PubmedUpdateFileCounts.empty(), false));

      job.execute();

      PubmedUpdateSyncCommand command = captor.getValue();
      assertThat(command.updateUrl()).isEqualTo("https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/");
      assertThat(command.filePrefix()).isEqualTo("pubmed26n");
      assertThat(command.startSeq()).isEqualTo(1335);
      assertThat(command.scope()).isEqualTo(PubmedUpdateScope.EXISTING_ONLY);
      assertThat(command.fromFile()).isNull();
      assertThat(command.maxFiles()).isNull();
      xxl.verify(() -> XxlJobHelper.handleSuccess(contains("处理 2 个文件")));
    }
  }

  @Test
  @DisplayName("任务参数覆盖 fromFile、maxFiles 与 scope")
  void should_parse_job_params() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn("fromFile=1400, maxFiles=3,scope=ALL");
      ArgumentCaptor<PubmedUpdateSyncCommand> captor =
          ArgumentCaptor.forClass(PubmedUpdateSyncCommand.class);
      when(commandBus.handle(captor.capture()))
          .thenReturn(PubmedUpdateSyncResult.of(3, 1402, PubmedUpdateFileCounts.empty(), false));

      job.execute();

      assertThat(captor.getValue().fromFile()).isEqualTo(1400);
      assertThat(captor.getValue().maxFiles()).isEqualTo(3);
      assertThat(captor.getValue().scope()).isEqualTo(PubmedUpdateScope.ALL);
    }
  }

  @Test
  @DisplayName("未知或重复的参数报参数错误，不执行")
  void should_fail_on_unknown_or_duplicate_param() {
    try (MockedStatic<XxlJobHelper> xxl = mockStatic(XxlJobHelper.class)) {
      xxl.when(XxlJobHelper::getJobParam).thenReturn("fromFile=1,fromFile=2");
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
      when(commandBus.handle(any(PubmedUpdateSyncCommand.class)))
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
      when(commandBus.handle(any(PubmedUpdateSyncCommand.class)))
          .thenReturn(PubmedUpdateSyncResult.ofSkippedByLock());

      job.execute();

      xxl.verify(() -> XxlJobHelper.handleSuccess(contains("跳过")));
    }
  }
}
