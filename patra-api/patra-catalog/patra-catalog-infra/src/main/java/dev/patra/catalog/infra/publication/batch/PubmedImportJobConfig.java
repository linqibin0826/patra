package dev.patra.catalog.infra.publication.batch;

import dev.patra.catalog.domain.kernel.port.source.FileDownloadPort;
import dev.patra.catalog.domain.organization.port.read.FunderLookupPort;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import dev.patra.catalog.domain.publication.port.gateway.VenueInstanceGateway;
import dev.patra.catalog.domain.publication.port.lookup.LanguageLookupPort;
import dev.patra.catalog.domain.publication.port.parser.PubmedXmlParserPort;
import dev.patra.catalog.domain.publication.port.repository.PublicationRepository;
import dev.patra.catalog.domain.publication.port.repository.PublicationSyncRepository;
import dev.patra.catalog.domain.venue.port.read.VenueLookupPort;
import dev.patra.catalog.infra.publication.batch.update.PublicationSyncItem;
import dev.patra.catalog.infra.publication.batch.update.PublicationSyncItemWriter;
import dev.patra.catalog.infra.publication.batch.update.PubmedDeleteCitationTasklet;
import dev.patra.catalog.infra.publication.batch.update.PubmedImportContextKeys;
import dev.patra.catalog.infra.publication.batch.update.PubmedImportCounters;
import dev.patra.catalog.infra.publication.batch.update.PubmedImportStepListener;
import dev.patra.tool.batch.autoconfigure.BatchProperties;
import dev.patra.tool.batch.metrics.BatchProgressMetricsListener;
import java.time.Clock;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

/// PubMed 导入作业配置：文件导入与本地重算两个作业，共用处理器与写入器（spec 第 5.1 节）。
///
/// - `pubmedFileImportJob`：读一个文件（baseline 或 updatefiles，下载后校验 md5）→ 解析 + 来源位置守卫 → 写入
///   文献与来源表；再按 `DeleteCitation` 软删（带守卫，`ALL` 范围写墓碑）
/// - `pubmedReparseJob`：从来源表读原文 → 同一个处理器（只接受同一位置的未软删文献）→ 同一个写入器
///
/// 作业与 Step 上下文只放字符串、整数、字符串列表（Spring Batch 只认 `java.*` 类型）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
public class PubmedImportJobConfig {

  private static final int SKIP_LIMIT = 1000;

  /// 下载与 md5 校验最多尝试次数（spec 第 5.7 节）。
  private static final int FETCH_ATTEMPTS = 3;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final PubmedXmlParserPort pubmedXmlParserPort;
  private final PublicationRepository publicationRepository;
  private final PublicationSyncRepository publicationSyncRepository;
  private final VenueInstanceGateway venueInstanceGateway;
  private final BatchProperties batchProperties;
  private final Clock clock;
  private final BatchProgressMetricsListener batchProgressMetricsListener;

  /// 创建配置。
  ///
  /// @param jobRepository 作业仓库
  /// @param transactionManager 事务管理器
  /// @param pubmedXmlParserPort PubMed 解析端口
  /// @param publicationRepository 文献仓储（新增）
  /// @param publicationSyncRepository 覆盖更新仓储
  /// @param venueInstanceGateway 期刊实例网关
  /// @param batchProperties 批处理配置
  /// @param clock 时钟
  /// @param batchProgressMetricsListener 批处理进度指标监听器（可选）
  public PubmedImportJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      PubmedXmlParserPort pubmedXmlParserPort,
      PublicationRepository publicationRepository,
      PublicationSyncRepository publicationSyncRepository,
      VenueInstanceGateway venueInstanceGateway,
      BatchProperties batchProperties,
      Clock clock,
      Optional<BatchProgressMetricsListener> batchProgressMetricsListener) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.pubmedXmlParserPort = pubmedXmlParserPort;
    this.publicationRepository = publicationRepository;
    this.publicationSyncRepository = publicationSyncRepository;
    this.venueInstanceGateway = venueInstanceGateway;
    this.batchProperties = batchProperties;
    this.clock = clock;
    this.batchProgressMetricsListener = batchProgressMetricsListener.orElse(null);
  }

  /// 文件导入作业。
  ///
  /// @param pubmedFileArticleStep 文章 Step
  /// @param pubmedDeleteCitationStep 删除 Step
  /// @return 作业
  @Bean
  public Job pubmedFileImportJob(Step pubmedFileArticleStep, Step pubmedDeleteCitationStep) {
    return new JobBuilder("pubmedFileImportJob", jobRepository)
        .start(pubmedFileArticleStep)
        .next(pubmedDeleteCitationStep)
        .build();
  }

  /// 本地重算作业。
  ///
  /// @param pubmedReparseStep 重算 Step
  /// @return 作业
  @Bean
  public Job pubmedReparseJob(Step pubmedReparseStep) {
    return new JobBuilder("pubmedReparseJob", jobRepository).start(pubmedReparseStep).build();
  }

  /// 文件导入的文章 Step：删除列表从 Step 上下文提升到作业上下文，供删除 Step 读取。
  ///
  /// @param pubmedFileItemReader 文件读取器
  /// @param pubmedArticleSyncProcessor 处理器
  /// @param publicationSyncItemWriter 写入器
  /// @param pubmedImportCounters 计数器
  /// @return Step
  @Bean
  public Step pubmedFileArticleStep(
      PubmedFileItemReader pubmedFileItemReader,
      PubmedArticleSyncProcessor pubmedArticleSyncProcessor,
      PublicationSyncItemWriter publicationSyncItemWriter,
      PubmedImportCounters pubmedImportCounters) {
    ExecutionContextPromotionListener promotion = new ExecutionContextPromotionListener();
    promotion.setKeys(new String[] {PubmedImportContextKeys.DELETED_PMIDS});
    return articleStep(
        "pubmedFileArticleStep",
        pubmedFileItemReader,
        pubmedArticleSyncProcessor,
        publicationSyncItemWriter,
        pubmedImportCounters,
        promotion);
  }

  /// 本地重算 Step。
  ///
  /// @param pubmedSourceRecordReader 来源表读取器
  /// @param pubmedArticleSyncProcessor 处理器
  /// @param publicationSyncItemWriter 写入器
  /// @param pubmedImportCounters 计数器
  /// @return Step
  @Bean
  public Step pubmedReparseStep(
      PubmedSourceRecordReader pubmedSourceRecordReader,
      PubmedArticleSyncProcessor pubmedArticleSyncProcessor,
      PublicationSyncItemWriter publicationSyncItemWriter,
      PubmedImportCounters pubmedImportCounters) {
    return articleStep(
        "pubmedReparseStep",
        pubmedSourceRecordReader,
        pubmedArticleSyncProcessor,
        publicationSyncItemWriter,
        pubmedImportCounters,
        null);
  }

  /// 删除 Step。
  ///
  /// @return Step
  @Bean
  public Step pubmedDeleteCitationStep() {
    return new StepBuilder("pubmedDeleteCitationStep", jobRepository)
        .tasklet(
            new PubmedDeleteCitationTasklet(publicationSyncRepository, clock), transactionManager)
        .build();
  }

  /// 下载与 md5 校验。
  ///
  /// @param fileDownloadPort 文件下载端口
  /// @return 抓取器
  @Bean
  public PubmedFileFetcher pubmedFileFetcher(FileDownloadPort fileDownloadPort) {
    return new PubmedFileFetcher(fileDownloadPort, FETCH_ATTEMPTS);
  }

  /// 文件读取器。
  ///
  /// @param pubmedFileFetcher 抓取器
  /// @param downloadUrl 文件下载地址
  /// @return 读取器
  @Bean
  @StepScope
  public PubmedFileItemReader pubmedFileItemReader(
      PubmedFileFetcher pubmedFileFetcher,
      @Value("#{jobParameters['downloadUrl']}") String downloadUrl) {
    return new PubmedFileItemReader(pubmedFileFetcher, downloadUrl);
  }

  /// 来源表读取器（本地重算）。
  ///
  /// @return 读取器
  @Bean
  @StepScope
  public PubmedSourceRecordReader pubmedSourceRecordReader() {
    return new PubmedSourceRecordReader(
        publicationSyncRepository, PubmedChunkSize.resolve(batchProperties));
  }

  /// 本 Step 共用的计数器。
  ///
  /// @return 计数器
  @Bean
  @StepScope
  public PubmedImportCounters pubmedImportCounters() {
    return new PubmedImportCounters();
  }

  /// 处理器：范围与是否重算取自作业参数。
  ///
  /// @param pubmedArticleItemProcessor 组装处理器
  /// @param scope 导入范围名
  /// @param reparse 是否本地重算（`"true"` / 空）
  /// @param pubmedImportCounters 计数器
  /// @return 处理器
  @Bean
  @StepScope
  public PubmedArticleSyncProcessor pubmedArticleSyncProcessor(
      PubmedArticleItemProcessor pubmedArticleItemProcessor,
      @Value("#{jobParameters['scope']}") String scope,
      @Value("#{jobParameters['reparse']}") String reparse,
      PubmedImportCounters pubmedImportCounters) {
    return new PubmedArticleSyncProcessor(
        pubmedXmlParserPort,
        publicationSyncRepository,
        pubmedArticleItemProcessor,
        PubmedImportScope.valueOf(scope),
        pubmedImportCounters,
        Boolean.parseBoolean(reparse));
  }

  /// 组装处理器（`CanonicalPublication` → `PublicationImportResult`）。
  ///
  /// @param venueLookupPort 期刊查找（批处理用）
  /// @param languageLookupPort 语言查找（批处理用）
  /// @param funderLookupPort 资助机构查找（批处理用）
  /// @param importBatch 批次名
  /// @return 组装处理器
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

  /// 新增文献的写入器（含 chunk 内与库内去重）。
  ///
  /// @param resultMapper 结果转换
  /// @param importBatch 批次名
  /// @return 写入器
  @Bean
  @StepScope
  public PublicationItemWriter publicationItemWriter(
      PublicationImportResultMapper resultMapper,
      @Value("#{jobParameters['importBatch']}") String importBatch) {
    return new PublicationItemWriter(publicationRepository, resultMapper, importBatch);
  }

  /// 新增 / 覆盖文献与来源表的写入器。
  ///
  /// @param publicationItemWriter 新增写入器
  /// @param resultMapper 结果转换
  /// @param pubmedImportCounters 计数器
  /// @return 写入器
  @Bean
  @StepScope
  public PublicationSyncItemWriter publicationSyncItemWriter(
      PublicationItemWriter publicationItemWriter,
      PublicationImportResultMapper resultMapper,
      PubmedImportCounters pubmedImportCounters) {
    return new PublicationSyncItemWriter(
        publicationSyncRepository,
        publicationItemWriter,
        resultMapper,
        pubmedImportCounters,
        clock);
  }

  /// 组装一个文章 Step（文件导入与本地重算共用）。
  ///
  /// @param name Step 名
  /// @param reader 读取器
  /// @param processor 处理器
  /// @param writer 写入器
  /// @param counters 计数器
  /// @param promotion 上下文提升监听器，可空
  /// @return Step
  private Step articleStep(
      String name,
      ItemStreamReader<PubmedSourceRecord> reader,
      PubmedArticleSyncProcessor processor,
      PublicationSyncItemWriter writer,
      PubmedImportCounters counters,
      ExecutionContextPromotionListener promotion) {
    int chunkSize = PubmedChunkSize.resolve(batchProperties);
    log.info("配置 {}，chunk size: {}", name, chunkSize);
    PubmedImportStepListener stepListener = new PubmedImportStepListener(counters);
    var builder =
        new StepBuilder(name, jobRepository)
            .<PubmedSourceRecord, PublicationSyncItem>chunk(chunkSize)
            .transactionManager(transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(SKIP_LIMIT)
            .skip(DataIntegrityViolationException.class)
            .skip(DuplicateKeyException.class)
            .skipListener(stepListener)
            .listener(stepListener);
    if (promotion != null) {
      builder.listener(promotion);
    }
    if (batchProgressMetricsListener != null) {
      builder.listener(batchProgressMetricsListener);
    }
    return builder.build();
  }
}
