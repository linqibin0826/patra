package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.vo.publication.pubmed.PubmedArticle;
import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.infra.adapter.batch.publication.cache.VenueCache;
import com.patra.starter.batch.config.BatchProperties;
import com.patra.starter.batch.metrics.BatchProgressMetricsListener;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/// PubMed Baseline 文献导入 Job 配置。
///
/// **Job 结构**：
///
/// ```
/// pubmedBaselineImportJob
///   └── pubmedArticleProcessingStep (chunk-oriented)
///         ├── reader: PubmedArticleItemReader
///         ├── processor: PubmedArticleItemProcessor
///         └── writer: PublicationItemWriter
/// ```
///
/// **单文件模式**：
///
/// 每次 Job 执行只处理一个 XML 文件（由 `downloadUrl` 参数指定）。
/// 这种设计便于控制导入范围：
/// - 测试环境：手动指定单个文件
/// - 生产环境：通过调度器循环执行 1~1274 个文件
///
/// **配置说明**：
///
/// - chunk size 默认 500（可通过 BatchProperties 调整）
/// - 支持 FaultTolerant 模式，单条失败不中断整个 Job
/// - 支持断点续传（PubmedArticleItemReader 实现 ItemStream）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
public class PubmedBaselineJobConfig {

  private static final int DEFAULT_CHUNK_SIZE = 500;
  private static final int DEFAULT_SKIP_LIMIT = 1000;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final StreamingDownloadPort streamingDownloadPort;
  private final PubmedXmlParserPort pubmedXmlParserPort;
  private final PublicationRepository publicationRepository;
  private final VenueRepository venueRepository;
  private final VenueInstanceGateway venueInstanceGateway;
  private final BatchProperties batchProperties;
  private final BatchProgressMetricsListener batchProgressMetricsListener;

  /// 构造函数。
  ///
  /// @param jobRepository Job 仓库
  /// @param transactionManager 事务管理器
  /// @param streamingDownloadPort 流式下载端口
  /// @param pubmedXmlParserPort PubMed XML 解析端口
  /// @param publicationRepository 文献仓库
  /// @param venueRepository 载体仓库
  /// @param venueInstanceGateway 载体实例端口
  /// @param batchProperties 批处理属性
  /// @param batchProgressMetricsListener 进度指标监听器（可选）
  public PubmedBaselineJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      StreamingDownloadPort streamingDownloadPort,
      PubmedXmlParserPort pubmedXmlParserPort,
      PublicationRepository publicationRepository,
      VenueRepository venueRepository,
      VenueInstanceGateway venueInstanceGateway,
      BatchProperties batchProperties,
      Optional<BatchProgressMetricsListener> batchProgressMetricsListener) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.streamingDownloadPort = streamingDownloadPort;
    this.pubmedXmlParserPort = pubmedXmlParserPort;
    this.publicationRepository = publicationRepository;
    this.venueRepository = venueRepository;
    this.venueInstanceGateway = venueInstanceGateway;
    this.batchProperties = batchProperties;
    this.batchProgressMetricsListener = batchProgressMetricsListener.orElse(null);
  }

  /// 测试用构造函数（不含可选监听器）。
  ///
  /// @param jobRepository Job 仓库
  /// @param transactionManager 事务管理器
  /// @param streamingDownloadPort 流式下载端口
  /// @param pubmedXmlParserPort PubMed XML 解析端口
  /// @param publicationRepository 文献仓库
  /// @param venueRepository 载体仓库
  /// @param venueInstanceGateway 载体实例端口
  /// @param batchProperties 批处理属性
  PubmedBaselineJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      StreamingDownloadPort streamingDownloadPort,
      PubmedXmlParserPort pubmedXmlParserPort,
      PublicationRepository publicationRepository,
      VenueRepository venueRepository,
      VenueInstanceGateway venueInstanceGateway,
      BatchProperties batchProperties) {
    this(
        jobRepository,
        transactionManager,
        streamingDownloadPort,
        pubmedXmlParserPort,
        publicationRepository,
        venueRepository,
        venueInstanceGateway,
        batchProperties,
        Optional.empty());
  }

  /// 配置 PubMed Baseline 导入 Job。
  ///
  /// @return Job 实例
  @Bean
  public Job pubmedBaselineImportJob() {
    return new JobBuilder("pubmedBaselineImportJob", jobRepository)
        .start(pubmedArticleProcessingStep())
        .build();
  }

  /// 配置 PubMed 文献处理 Step。
  ///
  /// @return Step 实例
  @Bean
  public Step pubmedArticleProcessingStep() {
    int chunkSize = getChunkSize();
    log.info("配置 pubmedArticleProcessingStep，chunk size: {}", chunkSize);

    var stepBuilder =
        new StepBuilder("pubmedArticleProcessingStep", jobRepository)
            .<PubmedArticle, PublicationAggregate>chunk(chunkSize, transactionManager)
            .reader(pubmedArticleItemReader(null))
            .processor(pubmedArticleItemProcessor())
            .writer(publicationItemWriter())
            .faultTolerant()
            .skipLimit(DEFAULT_SKIP_LIMIT)
            .skip(Exception.class);

    // 仅在指标监听器存在时注册
    if (batchProgressMetricsListener != null) {
      stepBuilder.listener(batchProgressMetricsListener);
      log.info("已注册 BatchProgressMetricsListener");
    }

    return stepBuilder.build();
  }

  /// 创建 PubMed 文献 ItemReader（StepScope）。
  ///
  /// @param downloadUrl XML 文件下载 URL（从 Job 参数注入）
  /// @return ItemReader 实例
  @Bean
  @StepScope
  public PubmedArticleItemReader pubmedArticleItemReader(
      @Value("#{jobParameters['downloadUrl']}") String downloadUrl) {
    return new PubmedArticleItemReader(streamingDownloadPort, pubmedXmlParserPort, downloadUrl);
  }

  /// 创建 PubMed 文献 ItemProcessor。
  ///
  /// @return ItemProcessor 实例
  @Bean
  public PubmedArticleItemProcessor pubmedArticleItemProcessor() {
    return new PubmedArticleItemProcessor(
        publicationRepository, venueCache(), venueInstanceGateway);
  }

  /// 创建 Publication ItemWriter。
  ///
  /// @return ItemWriter 实例
  @Bean
  public PublicationItemWriter publicationItemWriter() {
    return new PublicationItemWriter(publicationRepository);
  }

  /// 创建 Venue 缓存。
  ///
  /// 缓存所有 Venue 的 NLM ID 和 ISSN 索引，用于快速匹配。
  ///
  /// @return VenueCache 实例
  @Bean
  public VenueCache venueCache() {
    return new VenueCache(venueRepository);
  }

  /// 获取 chunk size。
  ///
  /// @return chunk size
  private int getChunkSize() {
    if (batchProperties != null && batchProperties.getChunk() != null) {
      int configuredSize = batchProperties.getChunk().getDefaultSize();
      if (configuredSize > 0) {
        return configuredSize;
      }
    }
    return DEFAULT_CHUNK_SIZE;
  }
}
