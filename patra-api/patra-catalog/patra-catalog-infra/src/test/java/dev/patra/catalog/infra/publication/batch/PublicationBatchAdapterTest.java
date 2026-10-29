package dev.patra.catalog.infra.publication.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileImportParams;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileRef;
import dev.patra.catalog.infra.publication.batch.update.PubmedImportContextKeys;
import dev.patra.tool.batch.core.JobOperatorHelper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/// `PublicationBatchAdapter` 单元测试：文件导入与本地重算的作业参数、已完成实例直接读计数、失败抛出。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationBatchAdapter 单元测试")
class PublicationBatchAdapterTest {

  private static final String BASELINE_URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/";
  private static final String UPDATE_URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/";

  private final JobOperatorHelper helper = mock(JobOperatorHelper.class);
  private final JobOperator jobOperator = mock(JobOperator.class);
  private final JobRepository jobRepository = mock(JobRepository.class);
  private final Job fileJob = mock(Job.class);
  private final Job reparseJob = mock(Job.class);
  private PublicationBatchAdapter adapter;

  @BeforeEach
  void setUp() {
    when(fileJob.getName()).thenReturn("pubmedFileImportJob");
    when(reparseJob.getName()).thenReturn("pubmedReparseJob");
    adapter = new PublicationBatchAdapter(helper, jobOperator, jobRepository, fileJob, reparseJob);
  }

  @Test
  @DisplayName("baseline 文件：下载地址取 baseline 目录，批次名 baseline- 前缀，范围照传")
  void should_launch_baseline_file_with_baseline_url_and_batch() {
    JobExecution done = execution(BatchStatus.COMPLETED, 3);
    when(helper.launch(eq(fileJob), any(), eq(false))).thenReturn(1L);
    when(helper.findJobExecution(1L)).thenReturn(Optional.of(done));

    PubmedFileCounts counts =
        adapter.launchFileImport(
            PubmedFileImportParams.of(
                PubmedFileRef.of(PubmedFileKind.BASELINE, "pubmed26n", 1333),
                BASELINE_URL,
                PubmedImportScope.EXISTING_ONLY,
                "replay-1"));

    ArgumentCaptor<PubmedFileImportJobParams> params =
        ArgumentCaptor.forClass(PubmedFileImportJobParams.class);
    verify(helper).launch(eq(fileJob), params.capture(), eq(false));
    assertThat(params.getValue().getDownloadUrl()).isEqualTo(BASELINE_URL + "pubmed26n1333.xml.gz");
    assertThat(params.getValue().getImportBatch()).isEqualTo("baseline-pubmed26n1333");
    assertThat(params.getValue().getScope()).isEqualTo("EXISTING_ONLY");
    assertThat(params.getValue().getGeneration()).isEqualTo("replay-1");
    assertThat(params.getValue().getReparse()).isNull();
    assertThat(counts.skippedStale()).isEqualTo(3);
  }

  @Test
  @DisplayName("update 文件：批次名 update- 前缀")
  void should_use_update_prefix_for_update_files() {
    when(helper.launch(eq(fileJob), any(), eq(false))).thenReturn(2L);
    JobExecution execution = execution(BatchStatus.COMPLETED, 0);
    when(helper.findJobExecution(2L)).thenReturn(Optional.of(execution));

    adapter.launchFileImport(
        PubmedFileImportParams.of(
            PubmedFileRef.of(PubmedFileKind.UPDATE, "pubmed26n", 1350),
            UPDATE_URL,
            PubmedImportScope.ALL,
            null));

    ArgumentCaptor<PubmedFileImportJobParams> params =
        ArgumentCaptor.forClass(PubmedFileImportJobParams.class);
    verify(helper).launch(eq(fileJob), params.capture(), eq(false));
    assertThat(params.getValue().getImportBatch()).isEqualTo("update-pubmed26n1350");
    assertThat(params.getValue().getDownloadUrl()).isEqualTo(UPDATE_URL + "pubmed26n1350.xml.gz");
  }

  @Test
  @DisplayName("同参数实例已完成：不再启动，直接读计数")
  void should_return_counts_of_completed_instance_without_launching() {
    JobExecution completed = execution(BatchStatus.COMPLETED, 5);
    when(jobRepository.getLastJobExecution(anyString(), any(JobParameters.class)))
        .thenReturn(completed);

    PubmedFileCounts counts =
        adapter.launchFileImport(
            PubmedFileImportParams.of(
                PubmedFileRef.of(PubmedFileKind.UPDATE, "pubmed26n", 1350),
                UPDATE_URL,
                PubmedImportScope.EXISTING_ONLY,
                null));

    assertThat(counts.skippedStale()).isEqualTo(5);
    verify(helper, never()).launch(any(), any(), eq(false));
  }

  @Test
  @DisplayName("作业失败时抛出")
  void should_throw_when_job_failed() {
    when(helper.launch(eq(fileJob), any(), eq(false))).thenReturn(3L);
    JobExecution execution = execution(BatchStatus.FAILED, 0);
    when(helper.findJobExecution(3L)).thenReturn(Optional.of(execution));

    assertThatThrownBy(
            () ->
                adapter.launchFileImport(
                    PubmedFileImportParams.of(
                        PubmedFileRef.of(PubmedFileKind.UPDATE, "pubmed26n", 1379),
                        UPDATE_URL,
                        PubmedImportScope.EXISTING_ONLY,
                        null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FAILED");
  }

  @Test
  @DisplayName("本地重算：没有下载地址，reparse=true，范围 EXISTING_ONLY，批次名 reparse")
  void should_launch_reparse_job() {
    when(helper.launch(eq(reparseJob), any(), eq(false))).thenReturn(4L);
    JobExecution execution = execution(BatchStatus.COMPLETED, 0);
    when(helper.findJobExecution(4L)).thenReturn(Optional.of(execution));

    adapter.launchReparse("reparse-9");

    ArgumentCaptor<PubmedFileImportJobParams> params =
        ArgumentCaptor.forClass(PubmedFileImportJobParams.class);
    verify(helper).launch(eq(reparseJob), params.capture(), eq(false));
    assertThat(params.getValue().getDownloadUrl()).isNull();
    assertThat(params.getValue().getReparse()).isEqualTo("true");
    assertThat(params.getValue().getScope()).isEqualTo("EXISTING_ONLY");
    assertThat(params.getValue().getImportBatch()).isEqualTo("reparse");
    assertThat(params.getValue().getGeneration()).isEqualTo("reparse-9");
  }

  private static JobExecution execution(BatchStatus status, int stale) {
    JobExecution execution = mock(JobExecution.class);
    when(execution.getStatus()).thenReturn(status);
    ExecutionContext ctx = new ExecutionContext();
    ctx.putInt(PubmedImportContextKeys.COUNT_SKIPPED_STALE, stale);
    when(execution.getExecutionContext()).thenReturn(ctx);
    return execution;
  }
}
