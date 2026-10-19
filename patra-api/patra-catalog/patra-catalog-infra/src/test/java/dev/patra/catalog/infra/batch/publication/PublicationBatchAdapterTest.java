package dev.patra.catalog.infra.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.catalog.domain.model.vo.publication.PublicationImportParams;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileCounts;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileRef;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateImportParams;
import dev.patra.catalog.infra.batch.publication.update.PubmedUpdateContextKeys;
import dev.patra.tool.batch.core.JobOperatorHelper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/// PublicationBatchAdapter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationBatchAdapter")
@ExtendWith(MockitoExtension.class)
class PublicationBatchAdapterTest {

  @Mock private JobOperatorHelper jobOperatorHelper;
  @Mock private Job pubmedBaselineImportJob;
  @Mock private Job pubmedUpdateImportJob;
  @Mock private JobRepository jobRepository;

  @Captor private ArgumentCaptor<PublicationImportJobParams> jobParamsCaptor;
  @Captor private ArgumentCaptor<PubmedUpdateImportJobParams> updateParamsCaptor;

  private PublicationBatchAdapter adapter;

  private static final String BASE_URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/";
  private static final int FILE_INDEX = 1;
  private static final Long EXPECTED_EXECUTION_ID = 12345L;

  @BeforeEach
  void setUp() {
    adapter =
        new PublicationBatchAdapter(
            jobOperatorHelper, jobRepository, pubmedBaselineImportJob, pubmedUpdateImportJob);
  }

  @Nested
  @DisplayName("launchBaselineImport()")
  class LaunchBaselineImportTest {

    @Test
    @DisplayName("应该启动 Job 并返回 Execution ID")
    void should_launch_job_and_return_execution_id() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX);
      when(jobOperatorHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);

      // when
      Long executionId = adapter.launchBaselineImport(params);

      // then
      assertThat(executionId).isEqualTo(EXPECTED_EXECUTION_ID);
    }

    @Test
    @DisplayName("应该传递正确的 Job 参数")
    void should_pass_correct_job_params() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX);
      when(jobOperatorHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);

      // when
      adapter.launchBaselineImport(params);

      // then
      verify(jobOperatorHelper)
          .launch(eq(pubmedBaselineImportJob), jobParamsCaptor.capture(), eq(false));

      PublicationImportJobParams captured = jobParamsCaptor.getValue();
      assertThat(captured.getDownloadUrl())
          .isEqualTo("https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/pubmed26n0001.xml.gz");
    }

    @Test
    @DisplayName("应该生成非空的 importBatch 参数")
    void should_generate_non_null_import_batch() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX);
      when(jobOperatorHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);

      // when
      adapter.launchBaselineImport(params);

      // then
      verify(jobOperatorHelper)
          .launch(eq(pubmedBaselineImportJob), jobParamsCaptor.capture(), eq(false));

      PublicationImportJobParams captured = jobParamsCaptor.getValue();
      assertThat(captured.getImportBatch()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("importBatch 应从 downloadUrl 中提取文件名标识")
    void should_extract_import_batch_from_download_url() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX);
      when(jobOperatorHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);

      // when
      adapter.launchBaselineImport(params);

      // then
      verify(jobOperatorHelper)
          .launch(eq(pubmedBaselineImportJob), jobParamsCaptor.capture(), eq(false));

      PublicationImportJobParams captured = jobParamsCaptor.getValue();
      // 从 pubmed26n0001.xml.gz 提取为 baseline-pubmed26n0001
      assertThat(captured.getImportBatch()).isEqualTo("baseline-pubmed26n0001");
    }

    @Test
    @DisplayName("应该把 generation 透传到 Job 参数")
    void should_pass_generation_to_job_params() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX, "v0.6");
      when(jobOperatorHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);

      // when
      adapter.launchBaselineImport(params);

      // then
      verify(jobOperatorHelper)
          .launch(eq(pubmedBaselineImportJob), jobParamsCaptor.capture(), eq(false));

      assertThat(jobParamsCaptor.getValue().getGeneration()).isEqualTo("v0.6");
    }

    @Test
    @DisplayName("未指定 generation 时 Job 参数应为 null")
    void should_leave_generation_null_when_absent() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX);
      when(jobOperatorHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);

      // when
      adapter.launchBaselineImport(params);

      // then
      verify(jobOperatorHelper)
          .launch(eq(pubmedBaselineImportJob), jobParamsCaptor.capture(), eq(false));

      assertThat(jobParamsCaptor.getValue().getGeneration()).isNull();
    }

    @Test
    @DisplayName("应该使用不添加时间戳的模式（支持断点续传）")
    void should_use_no_timestamp_mode_for_restart() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX);
      when(jobOperatorHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);

      // when
      adapter.launchBaselineImport(params);

      // then
      verify(jobOperatorHelper).launch(any(), any(), eq(false)); // addTimestamp = false
    }

    @Test
    @DisplayName("Job 同步执行失败时应该快速失败抛出异常")
    void should_fail_fast_when_job_execution_unsuccessful() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX);
      JobExecution jobExecution = mock(JobExecution.class);
      when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);

      when(jobOperatorHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);
      when(jobOperatorHelper.findJobExecution(EXPECTED_EXECUTION_ID))
          .thenReturn(Optional.of(jobExecution));

      // when / then
      assertThatThrownBy(() -> adapter.launchBaselineImport(params))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("executionId=" + EXPECTED_EXECUTION_ID)
          .hasMessageContaining("status=FAILED");
    }

    @Test
    @DisplayName("Job 执行记录不存在时不应因状态检查失败")
    void should_not_fail_when_job_execution_not_found() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX);
      when(jobOperatorHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);
      when(jobOperatorHelper.findJobExecution(EXPECTED_EXECUTION_ID)).thenReturn(Optional.empty());

      // when
      Long executionId = adapter.launchBaselineImport(params);

      // then
      assertThat(executionId).isEqualTo(EXPECTED_EXECUTION_ID);
      verify(jobOperatorHelper).findJobExecution(EXPECTED_EXECUTION_ID);
    }
  }

  @Nested
  @DisplayName("launchUpdateImport()")
  class LaunchUpdateImportTest {

    private final PubmedUpdateImportParams params =
        PubmedUpdateImportParams.of(
            PubmedUpdateFileRef.of("pubmed26n", 1335),
            "https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/",
            PubmedUpdateScope.EXISTING_ONLY,
            null);

    @Test
    @DisplayName("以不带时间戳的标识参数启动更新 Job，并从上下文读回计数")
    void should_launch_update_job_with_identifying_params() {
      when(pubmedUpdateImportJob.getName()).thenReturn("pubmedUpdateImportJob");
      when(jobRepository.getLastJobExecution(eq("pubmedUpdateImportJob"), any(JobParameters.class)))
          .thenReturn(null);
      when(jobOperatorHelper.launch(
              eq(pubmedUpdateImportJob), updateParamsCaptor.capture(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);
      JobExecution execution = completedExecution(countsContext(3, 2));
      when(jobOperatorHelper.findJobExecution(EXPECTED_EXECUTION_ID))
          .thenReturn(Optional.of(execution));

      PubmedUpdateFileCounts counts = adapter.launchUpdateImport(params);

      PubmedUpdateImportJobParams sent = updateParamsCaptor.getValue();
      assertThat(sent.getDownloadUrl())
          .isEqualTo("https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/pubmed26n1335.xml.gz");
      assertThat(sent.getImportBatch()).isEqualTo("update-pubmed26n1335");
      assertThat(sent.getScope()).isEqualTo("EXISTING_ONLY");
      assertThat(sent.getGeneration()).isNull();
      assertThat(counts.updated()).isEqualTo(3);
      assertThat(counts.deleted()).isEqualTo(2);
    }

    @Test
    @DisplayName("同参数的作业已经完成时不再启动，直接读它的计数")
    void should_return_counts_of_completed_execution_without_relaunch() {
      when(pubmedUpdateImportJob.getName()).thenReturn("pubmedUpdateImportJob");
      JobExecution execution = completedExecution(countsContext(5, 1));
      when(jobRepository.getLastJobExecution(eq("pubmedUpdateImportJob"), any(JobParameters.class)))
          .thenReturn(execution);

      PubmedUpdateFileCounts counts = adapter.launchUpdateImport(params);

      verify(jobOperatorHelper, never()).launch(any(), any(), eq(false));
      assertThat(counts.updated()).isEqualTo(5);
      assertThat(counts.deleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("作业失败时抛异常")
    void should_throw_when_update_job_failed() {
      when(pubmedUpdateImportJob.getName()).thenReturn("pubmedUpdateImportJob");
      when(jobOperatorHelper.launch(eq(pubmedUpdateImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);
      JobExecution execution = mock(JobExecution.class);
      when(execution.getStatus()).thenReturn(BatchStatus.FAILED);
      when(jobOperatorHelper.findJobExecution(EXPECTED_EXECUTION_ID))
          .thenReturn(Optional.of(execution));

      assertThatThrownBy(() -> adapter.launchUpdateImport(params))
          .isInstanceOf(IllegalStateException.class);
    }

    private ExecutionContext countsContext(int updated, int deleted) {
      ExecutionContext ctx = new ExecutionContext();
      ctx.putInt(PubmedUpdateContextKeys.COUNT_UPDATED, updated);
      ctx.putInt(PubmedUpdateContextKeys.COUNT_DELETED, deleted);
      return ctx;
    }

    private JobExecution completedExecution(ExecutionContext ctx) {
      JobExecution execution = mock(JobExecution.class);
      when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
      when(execution.getExecutionContext()).thenReturn(ctx);
      return execution;
    }
  }
}
