package com.patra.catalog.infra.adapter.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.vo.publication.PublicationImportParams;
import com.patra.starter.batch.core.JobLauncherHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;

/// PublicationBatchAdapter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationBatchAdapter")
@ExtendWith(MockitoExtension.class)
class PublicationBatchAdapterTest {

  @Mock private JobLauncherHelper jobLauncherHelper;
  @Mock private Job pubmedBaselineImportJob;

  @Captor private ArgumentCaptor<PublicationImportJobParams> jobParamsCaptor;

  private PublicationBatchAdapter adapter;

  private static final String BASE_URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/";
  private static final int FILE_INDEX = 1;
  private static final Long EXPECTED_EXECUTION_ID = 12345L;

  @BeforeEach
  void setUp() {
    adapter = new PublicationBatchAdapter(jobLauncherHelper, pubmedBaselineImportJob);
  }

  @Nested
  @DisplayName("launchBaselineImport()")
  class LaunchBaselineImportTest {

    @Test
    @DisplayName("应该启动 Job 并返回 Execution ID")
    void should_launch_job_and_return_execution_id() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX);
      when(jobLauncherHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
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
      when(jobLauncherHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);

      // when
      adapter.launchBaselineImport(params);

      // then
      verify(jobLauncherHelper)
          .launch(eq(pubmedBaselineImportJob), jobParamsCaptor.capture(), eq(false));

      PublicationImportJobParams captured = jobParamsCaptor.getValue();
      assertThat(captured.getDownloadUrl())
          .isEqualTo("https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/pubmed25n0001.xml.gz");
    }

    @Test
    @DisplayName("应该使用不添加时间戳的模式（支持断点续传）")
    void should_use_no_timestamp_mode_for_restart() {
      // given
      PublicationImportParams params = PublicationImportParams.of(BASE_URL, FILE_INDEX);
      when(jobLauncherHelper.launch(eq(pubmedBaselineImportJob), any(), eq(false)))
          .thenReturn(EXPECTED_EXECUTION_ID);

      // when
      adapter.launchBaselineImport(params);

      // then
      verify(jobLauncherHelper).launch(any(), any(), eq(false)); // addTimestamp = false
    }
  }
}
