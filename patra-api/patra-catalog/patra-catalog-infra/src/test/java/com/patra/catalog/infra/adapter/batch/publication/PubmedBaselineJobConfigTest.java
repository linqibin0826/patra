package com.patra.catalog.infra.adapter.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.infra.adapter.batch.publication.cache.VenueCache;
import com.patra.starter.batch.config.BatchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

/// PubmedBaselineJobConfig 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedBaselineJobConfig")
@ExtendWith(MockitoExtension.class)
class PubmedBaselineJobConfigTest {

  @Mock private JobRepository jobRepository;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private StreamingDownloadPort streamingDownloadPort;
  @Mock private PubmedXmlParserPort pubmedXmlParserPort;
  @Mock private PublicationRepository publicationRepository;
  @Mock private VenueRepository venueRepository;
  @Mock private VenueInstanceGateway venueInstanceGateway;
  @Mock private BatchProperties batchProperties;

  private PubmedBaselineJobConfig jobConfig;

  @BeforeEach
  void setUp() {
    jobConfig =
        new PubmedBaselineJobConfig(
            jobRepository,
            transactionManager,
            streamingDownloadPort,
            pubmedXmlParserPort,
            publicationRepository,
            venueRepository,
            venueInstanceGateway,
            batchProperties);
  }

  @Nested
  @DisplayName("Job 配置")
  class JobConfigTest {

    @Test
    @DisplayName("应该创建名为 pubmedBaselineImportJob 的 Job")
    void should_create_job_with_correct_name() {
      // when
      Job job = jobConfig.pubmedBaselineImportJob();

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
      // when
      Step step = jobConfig.pubmedArticleProcessingStep();

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
      String downloadUrl = "https://example.com/pubmed25n0001.xml.gz";

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
      // when
      PubmedArticleItemProcessor processor = jobConfig.pubmedArticleItemProcessor();

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
      PublicationItemWriter writer = jobConfig.publicationItemWriter();

      // then
      assertThat(writer).isNotNull();
    }
  }

  @Nested
  @DisplayName("VenueCache 配置")
  class VenueCacheConfigTest {

    @Test
    @DisplayName("应该创建 VenueCache")
    void should_create_venue_cache() {
      // when
      VenueCache cache = jobConfig.venueCache();

      // then
      assertThat(cache).isNotNull();
    }
  }
}
