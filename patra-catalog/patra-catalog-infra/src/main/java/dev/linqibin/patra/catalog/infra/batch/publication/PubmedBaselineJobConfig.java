package dev.linqibin.patra.catalog.infra.batch.publication;

import dev.linqibin.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import dev.linqibin.patra.catalog.domain.port.lookup.FunderLookupPort;
import dev.linqibin.patra.catalog.domain.port.lookup.LanguageLookupPort;
import dev.linqibin.patra.catalog.domain.port.lookup.VenueLookupPort;
import dev.linqibin.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import dev.linqibin.patra.catalog.domain.port.repository.PublicationRepository;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.starter.batch.config.BatchProperties;
import com.patra.starter.batch.metrics.BatchProgressMetricsListener;
import dev.linqibin.patra.common.model.CanonicalPublication;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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
  private final FileDownloadPort fileDownloadPort;
  private final PubmedXmlParserPort pubmedXmlParserPort;
  private final PublicationRepository publicationRepository;
  private final VenueInstanceGateway venueInstanceGateway;
  private final BatchProperties batchProperties;
  private final BatchProgressMetricsListener batchProgressMetricsListener;

  /// 构造函数。
  ///
  /// @param jobRepository Job 仓库
  /// @param transactionManager 事务管理器
  /// @param fileDownloadPort 文件下载端口
  /// @param pubmedXmlParserPort PubMed XML 解析端口
  /// @param publicationRepository 文献仓库
  /// @param venueInstanceGateway 载体实例端口
  /// @param batchProperties 批处理属性
  /// @param batchProgressMetricsListener 进度指标监听器（可选）
  public PubmedBaselineJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FileDownloadPort fileDownloadPort,
      PubmedXmlParserPort pubmedXmlParserPort,
      PublicationRepository publicationRepository,
      VenueInstanceGateway venueInstanceGateway,
      BatchProperties batchProperties,
      Optional<BatchProgressMetricsListener> batchProgressMetricsListener) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.fileDownloadPort = fileDownloadPort;
    this.pubmedXmlParserPort = pubmedXmlParserPort;
    this.publicationRepository = publicationRepository;
    this.venueInstanceGateway = venueInstanceGateway;
    this.batchProperties = batchProperties;
    this.batchProgressMetricsListener = batchProgressMetricsListener.orElse(null);
  }

  /// 配置 PubMed Baseline 导入 Job。
  ///
  /// @param pubmedArticleProcessingStep 文献处理 Step（通过 Spring 注入）
  /// @return Job 实例
  @Bean
  public Job pubmedBaselineImportJob(Step pubmedArticleProcessingStep) {
    return new JobBuilder("pubmedBaselineImportJob", jobRepository)
        .start(pubmedArticleProcessingStep)
        .build();
  }

  /// 配置 PubMed 文献处理 Step。
  ///
  /// **注意**：Reader、Processor 和 Writer 通过方法参数注入。
  ///
  /// @param reader 文献读取器（@StepScope）
  /// @param processor 文献处理器（@StepScope）
  /// @param writer 文献写入器
  /// @return Step 实例
  @Bean
  public Step pubmedArticleProcessingStep(
      PubmedArticleItemReader reader,
      PubmedArticleItemProcessor processor,
      PublicationItemWriter writer) {
    int chunkSize = getChunkSize();
    log.info("配置 pubmedArticleProcessingStep，chunk size: {}", chunkSize);

    var stepBuilder =
        new StepBuilder("pubmedArticleProcessingStep", jobRepository)
            .<CanonicalPublication, PublicationImportResult>chunk(chunkSize)
            .transactionManager(transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(DEFAULT_SKIP_LIMIT)
            .skip(DataIntegrityViolationException.class)
            .skip(DuplicateKeyException.class);

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
    return new PubmedArticleItemReader(fileDownloadPort, pubmedXmlParserPort, downloadUrl);
  }

  /// 创建 PubMed 文献 ItemProcessor。
  ///
  /// **注意**：`VenueLookupPort`、`LanguageLookupPort` 和 `FunderLookupPort` 通过方法参数注入，
  /// 因为它们是 `@StepScope` Bean，不能在 Configuration 构造函数中注入。
  ///
  /// @param venueLookupPort Venue 查找端口（批处理专用，带缓存）
  /// @param languageLookupPort 语言查找端口（批处理专用，带缓存）
  /// @param funderLookupPort 资助机构查找端口（批处理专用，带缓存）
  /// @param importBatch 导入批次标识（从 Job 参数注入）
  /// @return ItemProcessor 实例
  @Bean
  @StepScope
  public PubmedArticleItemProcessor pubmedArticleItemProcessor(
      @Qualifier("batchVenueLookupAdapter") VenueLookupPort venueLookupPort,
      @Qualifier("batchLanguageLookupAdapter") LanguageLookupPort languageLookupPort,
      @Qualifier("batchFunderLookupAdapter") FunderLookupPort funderLookupPort,
      @Value("#{jobParameters['importBatch']}") String importBatch) {
    return new PubmedArticleItemProcessor(
        venueLookupPort, venueInstanceGateway, languageLookupPort, funderLookupPort, importBatch);
  }

  /// 创建 Publication ItemWriter。
  ///
  /// **重构说明**：
  ///
  /// 重构后 Writer 仅依赖 Repository 和 Mapper，不再直接依赖多个 DAO。
  /// 所有关联数据的写入逻辑已移至 PublicationRepositoryAdapter.insertAllWithAssociations()。
  ///
  /// @param resultMapper 结果转换器（Data → Domain 值对象）
  /// @param importBatch 导入批次标识（从 Job 参数注入）
  /// @return ItemWriter 实例
  @Bean
  @StepScope
  public PublicationItemWriter publicationItemWriter(
      PublicationImportResultMapper resultMapper,
      @Value("#{jobParameters['importBatch']}") String importBatch) {
    return new PublicationItemWriter(publicationRepository, resultMapper, importBatch);
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
