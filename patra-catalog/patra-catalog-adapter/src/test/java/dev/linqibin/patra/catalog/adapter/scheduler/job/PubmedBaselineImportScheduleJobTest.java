package dev.linqibin.patra.catalog.adapter.scheduler.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.adapter.scheduler.config.PubmedDataSourceProperties;
import dev.linqibin.patra.catalog.api.error.CatalogErrorCode;
import dev.linqibin.patra.catalog.app.usecase.publication.baseline.command.PublicationBaselineImportCommand;
import dev.linqibin.patra.catalog.app.usecase.publication.baseline.dto.PublicationBaselineImportResult;
import com.xxl.job.core.context.XxlJobContext;
import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.commons.error.ApplicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// PubmedBaselineImportScheduleJob 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedBaselineImportScheduleJob")
@ExtendWith(MockitoExtension.class)
class PubmedBaselineImportScheduleJobTest {

  @Mock private CommandBus commandBus;

  @Captor private ArgumentCaptor<PublicationBaselineImportCommand> commandCaptor;

  private PubmedDataSourceProperties properties;
  private PubmedBaselineImportScheduleJob scheduleJob;

  private static final String BASE_URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/";
  private static final Long EXECUTION_ID = 12345L;

  @BeforeEach
  void setUp() {
    properties = new PubmedDataSourceProperties();
    properties.setBaselineUrl(BASE_URL);
    scheduleJob = new PubmedBaselineImportScheduleJob(commandBus, properties);
  }

  @AfterEach
  void tearDown() {
    // 清理 XxlJobContext，避免测试污染
    XxlJobContext.setXxlJobContext(null);
  }

  @Nested
  @DisplayName("执行导入")
  class ExecuteImport {

    @Test
    @DisplayName("应该使用配置的 baseUrl 和指定的 fileIndex 创建命令")
    void should_create_command_with_config_and_file_index() {
      // given
      int fileIndex = 42;
      when(commandBus.handle(any(PublicationBaselineImportCommand.class)))
          .thenReturn(mockResult(fileIndex));

      // when
      scheduleJob.executeImport(fileIndex);

      // then
      verify(commandBus).handle(commandCaptor.capture());
      PublicationBaselineImportCommand captured = commandCaptor.getValue();
      assertThat(captured.baseUrl()).isEqualTo(BASE_URL);
      assertThat(captured.fileIndex()).isEqualTo(fileIndex);
    }

    @Test
    @DisplayName("应该使用默认 baseUrl")
    void should_use_default_base_url() {
      // given
      PubmedDataSourceProperties defaultProperties = new PubmedDataSourceProperties();
      PubmedBaselineImportScheduleJob job =
          new PubmedBaselineImportScheduleJob(commandBus, defaultProperties);
      when(commandBus.handle(any(PublicationBaselineImportCommand.class)))
          .thenReturn(mockResult(1));

      // when
      job.executeImport(1);

      // then
      verify(commandBus).handle(commandCaptor.capture());
      assertThat(commandCaptor.getValue().baseUrl())
          .isEqualTo("https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/");
    }
  }

  @Nested
  @DisplayName("execute() 方法参数解析")
  class Execute {

    @Test
    @DisplayName("缺少 fileIndex 参数时应该标记任务失败")
    void should_fail_when_file_index_missing() {
      // given
      XxlJobContext context = new XxlJobContext(1L, "", "", 0, 0);
      XxlJobContext.setXxlJobContext(context);

      // when
      scheduleJob.execute();

      // then
      assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
      assertThat(context.getHandleMsg()).contains("参数错误");
      assertThat(context.getHandleMsg()).contains("fileIndex");
    }

    @Test
    @DisplayName("fileIndex 参数格式无效时应该标记任务失败")
    void should_fail_when_file_index_format_invalid() {
      // given
      XxlJobContext context = new XxlJobContext(1L, "invalid=format", "", 0, 0);
      XxlJobContext.setXxlJobContext(context);

      // when
      scheduleJob.execute();

      // then
      assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
      assertThat(context.getHandleMsg()).contains("参数错误");
    }

    @Test
    @DisplayName("fileIndex 非整数时应该标记任务失败")
    void should_fail_when_file_index_not_integer() {
      // given
      XxlJobContext context = new XxlJobContext(1L, "fileIndex=abc", "", 0, 0);
      XxlJobContext.setXxlJobContext(context);

      // when
      scheduleJob.execute();

      // then
      assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
      assertThat(context.getHandleMsg()).contains("参数错误");
    }

    @Test
    @DisplayName("参数正确时应该成功执行导入")
    void should_succeed_when_parameter_valid() {
      // given
      XxlJobContext context = new XxlJobContext(1L, "fileIndex=1", "", 0, 0);
      XxlJobContext.setXxlJobContext(context);
      when(commandBus.handle(any(PublicationBaselineImportCommand.class)))
          .thenReturn(mockResult(1));

      // when
      scheduleJob.execute();

      // then
      assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    }

    @Test
    @DisplayName("CommandBus 抛出 ApplicationException 时应该标记任务失败")
    void should_fail_when_command_bus_throws_application_exception() {
      // given
      XxlJobContext context = new XxlJobContext(1L, "fileIndex=1", "", 0, 0);
      XxlJobContext.setXxlJobContext(context);
      when(commandBus.handle(any(PublicationBaselineImportCommand.class)))
          .thenThrow(new ApplicationException(CatalogErrorCode.CAT_1501, "导入失败"));

      // when
      scheduleJob.execute();

      // then
      assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
      assertThat(context.getHandleMsg()).contains("执行失败");
    }

    @Test
    @DisplayName("CommandBus 抛出运行时异常时应该标记任务失败")
    void should_fail_when_command_bus_throws_runtime_exception() {
      // given
      XxlJobContext context = new XxlJobContext(1L, "fileIndex=1", "", 0, 0);
      XxlJobContext.setXxlJobContext(context);
      when(commandBus.handle(any(PublicationBaselineImportCommand.class)))
          .thenThrow(new RuntimeException("网络错误"));

      // when
      scheduleJob.execute();

      // then
      assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
      assertThat(context.getHandleMsg()).contains("执行失败");
    }
  }

  /// 创建模拟结果。
  private PublicationBaselineImportResult mockResult(int fileIndex) {
    return PublicationBaselineImportResult.success(
        EXECUTION_ID, BASE_URL, fileIndex, "pubmed26n%04d.xml.gz".formatted(fileIndex));
  }
}
