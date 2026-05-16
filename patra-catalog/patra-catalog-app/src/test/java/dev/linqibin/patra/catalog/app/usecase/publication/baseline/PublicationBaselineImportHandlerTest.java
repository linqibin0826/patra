package dev.linqibin.patra.catalog.app.usecase.publication.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.app.usecase.publication.baseline.command.PublicationBaselineImportCommand;
import dev.linqibin.patra.catalog.app.usecase.publication.baseline.dto.PublicationBaselineImportResult;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationImportParams;
import dev.linqibin.patra.catalog.domain.port.batch.PublicationBatchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// PublicationBaselineImportHandler 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationBaselineImportHandler")
@ExtendWith(MockitoExtension.class)
class PublicationBaselineImportHandlerTest {

  @Mock private PublicationBatchPort publicationBatchPort;

  @Captor private ArgumentCaptor<PublicationImportParams> paramsCaptor;

  private PublicationBaselineImportHandler handler;

  private static final String BASE_URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/";
  private static final int FILE_INDEX = 42;
  private static final Long EXECUTION_ID = 12345L;

  @BeforeEach
  void setUp() {
    handler = new PublicationBaselineImportHandler(publicationBatchPort);
  }

  @Nested
  @DisplayName("handle()")
  class HandleTest {

    @Test
    @DisplayName("应该启动批处理任务并返回结果")
    void should_launch_batch_job_and_return_result() {
      // given
      var command = PublicationBaselineImportCommand.of(BASE_URL, FILE_INDEX);
      when(publicationBatchPort.launchBaselineImport(any())).thenReturn(EXECUTION_ID);

      // when
      PublicationBaselineImportResult result = handler.handle(command);

      // then
      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(EXECUTION_ID);
      assertThat(result.baseUrl()).isEqualTo(BASE_URL);
      assertThat(result.fileIndex()).isEqualTo(FILE_INDEX);
      assertThat(result.fileName()).isEqualTo("pubmed26n0042.xml.gz");
    }

    @Test
    @DisplayName("应该传递正确的导入参数给 BatchPort")
    void should_pass_correct_params_to_batch_port() {
      // given
      var command = PublicationBaselineImportCommand.of(BASE_URL, FILE_INDEX);
      when(publicationBatchPort.launchBaselineImport(any())).thenReturn(EXECUTION_ID);

      // when
      handler.handle(command);

      // then
      verify(publicationBatchPort).launchBaselineImport(paramsCaptor.capture());
      PublicationImportParams captured = paramsCaptor.getValue();
      assertThat(captured.baseUrl()).isEqualTo(BASE_URL);
      assertThat(captured.fileIndex()).isEqualTo(FILE_INDEX);
    }

    @Test
    @DisplayName("结果消息应该包含执行信息")
    void should_include_execution_info_in_message() {
      // given
      var command = PublicationBaselineImportCommand.of(BASE_URL, FILE_INDEX);
      when(publicationBatchPort.launchBaselineImport(any())).thenReturn(EXECUTION_ID);

      // when
      PublicationBaselineImportResult result = handler.handle(command);

      // then
      assertThat(result.message())
          .contains(EXECUTION_ID.toString())
          .contains(String.valueOf(FILE_INDEX))
          .contains("pubmed26n0042.xml.gz");
    }
  }
}
