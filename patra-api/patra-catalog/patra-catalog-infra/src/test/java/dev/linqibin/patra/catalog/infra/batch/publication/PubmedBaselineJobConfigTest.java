package dev.linqibin.patra.catalog.infra.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.batch.config.BatchProperties;
import dev.linqibin.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import dev.linqibin.patra.catalog.domain.port.lookup.FunderLookupPort;
import dev.linqibin.patra.catalog.domain.port.lookup.LanguageLookupPort;
import dev.linqibin.patra.catalog.domain.port.lookup.VenueLookupPort;
import dev.linqibin.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import dev.linqibin.patra.catalog.domain.port.repository.PublicationRepository;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

/// PubmedBaselineJobConfig 单元测试。
///
/// **重构后的测试策略**：
///
/// 重构后 JobConfig 的依赖大幅简化：
/// - 移除 15+ 个 DAO 依赖
/// - Writer 仅依赖 PublicationRepository 和 PublicationImportResultMapper
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedBaselineJobConfig")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PubmedBaselineJobConfigTest {

  @Mock private JobRepository jobRepository;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private FileDownloadPort fileDownloadPort;
  @Mock private PubmedXmlParserPort pubmedXmlParserPort;
  @Mock private PublicationRepository publicationRepository;
  @Mock private VenueLookupPort venueLookupPort;
  @Mock private LanguageLookupPort languageLookupPort;
  @Mock private FunderLookupPort funderLookupPort;
  @Mock private VenueInstanceGateway venueInstanceGateway;
  @Mock private BatchProperties batchProperties;
  @Mock private PublicationImportResultMapper resultMapper;

  private PubmedBaselineJobConfig jobConfig;

  @BeforeEach
  void setUp() {
    jobConfig =
        new PubmedBaselineJobConfig(
            jobRepository,
            transactionManager,
            fileDownloadPort,
            pubmedXmlParserPort,
            publicationRepository,
            venueInstanceGateway,
            batchProperties,
            Optional.empty());
  }

  @Nested
  @DisplayName("Job 配置")
  class JobConfigTest {

    @Test
    @DisplayName("应该创建名为 pubmedBaselineImportJob 的 Job")
    void should_create_job_with_correct_name() {
      // given - 创建 Step 依赖
      PubmedArticleItemReader reader =
          jobConfig.pubmedArticleItemReader("https://example.com/test.xml.gz");
      PubmedArticleItemProcessor processor =
          jobConfig.pubmedArticleItemProcessor(
              venueLookupPort, languageLookupPort, funderLookupPort, "test-batch");
      PublicationItemWriter writer = jobConfig.publicationItemWriter(resultMapper, "test-batch");
      Step step = jobConfig.pubmedArticleProcessingStep(reader, processor, writer);

      // when
      Job job = jobConfig.pubmedBaselineImportJob(step);

      // then
      assertThat(job).isNotNull();
      assertThat(job.getName()).isEqualTo("pubmedBaselineImportJob");
    }
  }

  @Nested
  @DisplayName("Step 配置")
  class StepConfigTest {

    @Test
    @DisplayName("应该创建名为 pubmedArticleProcessingStep 的 Step")
    void should_create_step_with_correct_name() {
      // given - 创建 Reader、Processor 和 Writer 依赖
      PubmedArticleItemReader reader =
          jobConfig.pubmedArticleItemReader("https://example.com/test.xml.gz");
      PubmedArticleItemProcessor processor =
          jobConfig.pubmedArticleItemProcessor(
              venueLookupPort, languageLookupPort, funderLookupPort, "test-batch");
      PublicationItemWriter writer = jobConfig.publicationItemWriter(resultMapper, "test-batch");

      // when
      Step step = jobConfig.pubmedArticleProcessingStep(reader, processor, writer);

      // then
      assertThat(step).isNotNull();
      assertThat(step.getName()).isEqualTo("pubmedArticleProcessingStep");
    }
  }

  @Nested
  @DisplayName("Reader 配置")
  class ReaderConfigTest {

    @Test
    @DisplayName("应该创建 PubmedArticleItemReader")
    void should_create_item_reader() {
      // given
      String downloadUrl = "https://example.com/pubmed26n0001.xml.gz";

      // when
      PubmedArticleItemReader reader = jobConfig.pubmedArticleItemReader(downloadUrl);

      // then
      assertThat(reader).isNotNull();
    }
  }

  @Nested
  @DisplayName("Processor 配置")
  class ProcessorConfigTest {

    @Test
    @DisplayName("应该创建 PubmedArticleItemProcessor")
    void should_create_item_processor() {
      // when - 通过方法参数传入 @StepScope beans
      PubmedArticleItemProcessor processor =
          jobConfig.pubmedArticleItemProcessor(
              venueLookupPort, languageLookupPort, funderLookupPort, "test-batch");

      // then
      assertThat(processor).isNotNull();
    }
  }

  @Nested
  @DisplayName("Writer 配置")
  class WriterConfigTest {

    @Test
    @DisplayName("应该创建 PublicationItemWriter")
    void should_create_item_writer() {
      // when
      PublicationItemWriter writer = jobConfig.publicationItemWriter(resultMapper, "test-batch");

      // then
      assertThat(writer).isNotNull();
    }
  }
}
