package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.lookup.FunderLookupPort;
import com.patra.catalog.domain.port.lookup.LanguageLookupPort;
import com.patra.catalog.domain.port.lookup.VenueLookupPort;
import com.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationAlternativeAbstractDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationDateDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationFundingDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationIdentifierDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationKeywordDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMeshHeadingDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMeshQualifierDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationSupplMeshDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationTypeDao;
import com.patra.common.model.CanonicalPublication;
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
import org.springframework.beans.factory.annotation.Qualifier;
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
  private final VenueInstanceGateway venueInstanceGateway;
  private final PublicationMeshHeadingDao meshHeadingDao;
  private final PublicationMeshQualifierDao meshQualifierDao;
  private final PublicationKeywordDao keywordDao;
  private final PublicationFundingDao fundingDao;
  private final PublicationTypeDao typeDao;
  private final PublicationSupplMeshDao supplMeshDao;
  private final PublicationAlternativeAbstractDao alternativeAbstractDao;
  private final PublicationDateDao dateDao;
  private final PublicationIdentifierDao identifierDao;
  private final BatchProperties batchProperties;
  private final BatchProgressMetricsListener batchProgressMetricsListener;

  /// 构造函数。
  ///
  /// @param jobRepository Job 仓库
  /// @param transactionManager 事务管理器
  /// @param streamingDownloadPort 流式下载端口
  /// @param pubmedXmlParserPort PubMed XML 解析端口
  /// @param publicationRepository 文献仓库
  /// @param venueInstanceGateway 载体实例端口
  /// @param meshHeadingDao MeSH Heading DAO
  /// @param meshQualifierDao MeSH Qualifier DAO
  /// @param keywordDao 关键词 DAO
  /// @param fundingDao 资助信息 DAO
  /// @param typeDao 出版类型 DAO
  /// @param supplMeshDao 补充 MeSH 概念 DAO
  /// @param alternativeAbstractDao 翻译摘要 DAO
  /// @param dateDao 日期 DAO
  /// @param identifierDao 标识符 DAO
  /// @param batchProperties 批处理属性
  /// @param batchProgressMetricsListener 进度指标监听器（可选）
  public PubmedBaselineJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      StreamingDownloadPort streamingDownloadPort,
      PubmedXmlParserPort pubmedXmlParserPort,
      PublicationRepository publicationRepository,
      VenueInstanceGateway venueInstanceGateway,
      PublicationMeshHeadingDao meshHeadingDao,
      PublicationMeshQualifierDao meshQualifierDao,
      PublicationKeywordDao keywordDao,
      PublicationFundingDao fundingDao,
      PublicationTypeDao typeDao,
      PublicationSupplMeshDao supplMeshDao,
      PublicationAlternativeAbstractDao alternativeAbstractDao,
      PublicationDateDao dateDao,
      PublicationIdentifierDao identifierDao,
      BatchProperties batchProperties,
      Optional<BatchProgressMetricsListener> batchProgressMetricsListener) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.streamingDownloadPort = streamingDownloadPort;
    this.pubmedXmlParserPort = pubmedXmlParserPort;
    this.publicationRepository = publicationRepository;
    this.venueInstanceGateway = venueInstanceGateway;
    this.meshHeadingDao = meshHeadingDao;
    this.meshQualifierDao = meshQualifierDao;
    this.keywordDao = keywordDao;
    this.fundingDao = fundingDao;
    this.typeDao = typeDao;
    this.supplMeshDao = supplMeshDao;
    this.alternativeAbstractDao = alternativeAbstractDao;
    this.dateDao = dateDao;
    this.identifierDao = identifierDao;
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
  /// **注意**：Reader 和 Processor 通过方法参数注入，因为它们是 `@StepScope` Bean。
  ///
  /// @param reader 文献读取器（@StepScope）
  /// @param processor 文献处理器（@StepScope）
  /// @return Step 实例
  @Bean
  public Step pubmedArticleProcessingStep(
      PubmedArticleItemReader reader, PubmedArticleItemProcessor processor) {
    int chunkSize = getChunkSize();
    log.info("配置 pubmedArticleProcessingStep，chunk size: {}", chunkSize);

    var stepBuilder =
        new StepBuilder("pubmedArticleProcessingStep", jobRepository)
            .<CanonicalPublication, PublicationImportResult>chunk(chunkSize)
            .transactionManager(transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(
                publicationItemWriter(
                    meshHeadingDao,
                    meshQualifierDao,
                    keywordDao,
                    fundingDao,
                    typeDao,
                    supplMeshDao,
                    alternativeAbstractDao,
                    dateDao,
                    identifierDao))
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
  /// **注意**：`VenueLookupPort`、`LanguageLookupPort` 和 `FunderLookupPort` 通过方法参数注入，
  /// 因为它们是 `@StepScope` Bean，不能在 Configuration 构造函数中注入。
  ///
  /// @param venueLookupPort Venue 查找端口（批处理专用，带缓存）
  /// @param languageLookupPort 语言查找端口（批处理专用，带缓存）
  /// @param funderLookupPort 资助机构查找端口（批处理专用，带缓存）
  /// @return ItemProcessor 实例
  @Bean
  @StepScope
  public PubmedArticleItemProcessor pubmedArticleItemProcessor(
      @Qualifier("batchVenueLookupAdapter") VenueLookupPort venueLookupPort,
      @Qualifier("batchLanguageLookupAdapter") LanguageLookupPort languageLookupPort,
      @Qualifier("batchFunderLookupAdapter") FunderLookupPort funderLookupPort) {
    return new PubmedArticleItemProcessor(
        publicationRepository,
        venueLookupPort,
        venueInstanceGateway,
        languageLookupPort,
        funderLookupPort);
  }

  /// 创建 Publication ItemWriter。
  ///
  /// @param headingDao MeSH Heading DAO
  /// @param qualifierDao MeSH Qualifier DAO
  /// @param kwDao 关键词 DAO
  /// @param fundDao 资助信息 DAO
  /// @param ptDao 出版类型 DAO
  /// @param supplMeshDao 补充 MeSH 概念 DAO
  /// @param altAbstractDao 翻译摘要 DAO
  /// @param dateDao 日期 DAO
  /// @param idDao 标识符 DAO
  /// @return ItemWriter 实例
  @Bean
  public PublicationItemWriter publicationItemWriter(
      PublicationMeshHeadingDao headingDao,
      PublicationMeshQualifierDao qualifierDao,
      PublicationKeywordDao kwDao,
      PublicationFundingDao fundDao,
      PublicationTypeDao ptDao,
      PublicationSupplMeshDao supplMeshDao,
      PublicationAlternativeAbstractDao altAbstractDao,
      PublicationDateDao dateDao,
      PublicationIdentifierDao idDao) {
    return new PublicationItemWriter(
        publicationRepository,
        headingDao,
        qualifierDao,
        kwDao,
        fundDao,
        ptDao,
        supplMeshDao,
        altAbstractDao,
        dateDao,
        idDao);
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
