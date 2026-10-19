package dev.patra.catalog.infra.batch.publication.update;

import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import dev.patra.catalog.domain.port.repository.PublicationSyncRepository;
import dev.patra.catalog.domain.port.source.FileDownloadPort;
import dev.patra.catalog.infra.batch.publication.PublicationImportResultMapper;
import dev.patra.catalog.infra.batch.publication.PublicationItemWriter;
import dev.patra.catalog.infra.batch.publication.PubmedArticleItemProcessor;
import dev.patra.catalog.infra.batch.publication.PubmedChunkSize;
import dev.patra.common.CanonicalPublication;
import dev.patra.tool.batch.autoconfigure.BatchProperties;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

/// PubMed updatefiles 增量同步作业配置。
///
/// 作业 `pubmedUpdateImportJob` 处理一个文件：
///
/// 1. `pubmedUpdateArticleStep`（chunk）：读文章 → 分流处理器（委托 baseline 处理器）→ 覆盖 / 新建写入器；
/// 2. `pubmedDeleteCitationStep`（Tasklet）：按 `DeleteCitation` 软删除。
///
/// 处理器与插入写入器复用 baseline 配置里 `@StepScope` 的 `pubmedArticleItemProcessor`、
/// `publicationItemWriter`（两个作业都有 `importBatch` 参数）。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Configuration
public class PubmedUpdateJobConfig {

  private static final int SKIP_LIMIT = 1000;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final FileDownloadPort fileDownloadPort;
  private final PubmedXmlParserPort pubmedXmlParserPort;
  private final PublicationSyncRepository publicationSyncRepository;
  private final BatchProperties batchProperties;
  private final Clock clock;

  /// 创建配置。
  ///
  /// @param jobRepository 作业仓库
  /// @param transactionManager 事务管理器
  /// @param fileDownloadPort 文件下载端口
  /// @param pubmedXmlParserPort PubMed 解析端口
  /// @param publicationSyncRepository 覆盖更新仓储
  /// @param batchProperties 批处理配置
  /// @param clock 时钟
  public PubmedUpdateJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FileDownloadPort fileDownloadPort,
      PubmedXmlParserPort pubmedXmlParserPort,
      PublicationSyncRepository publicationSyncRepository,
      BatchProperties batchProperties,
      Clock clock) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.fileDownloadPort = fileDownloadPort;
    this.pubmedXmlParserPort = pubmedXmlParserPort;
    this.publicationSyncRepository = publicationSyncRepository;
    this.batchProperties = batchProperties;
    this.clock = clock;
  }

  /// 增量同步作业。
  ///
  /// @param pubmedUpdateArticleStep 文章 Step
  /// @param pubmedDeleteCitationStep 删除 Step
  /// @return 作业
  @Bean
  public Job pubmedUpdateImportJob(Step pubmedUpdateArticleStep, Step pubmedDeleteCitationStep) {
    return new JobBuilder("pubmedUpdateImportJob", jobRepository)
        .start(pubmedUpdateArticleStep)
        .next(pubmedDeleteCitationStep)
        .build();
  }

  /// 文章 Step。
  ///
  /// @param pubmedUpdateItemReader 读取器
  /// @param pubmedUpdateRoutingProcessor 分流处理器
  /// @param publicationSyncItemWriter 写入器
  /// @param pubmedUpdateCounters 计数器
  /// @return Step
  @Bean
  public Step pubmedUpdateArticleStep(
      PubmedUpdateItemReader pubmedUpdateItemReader,
      PubmedUpdateRoutingProcessor pubmedUpdateRoutingProcessor,
      PublicationSyncItemWriter publicationSyncItemWriter,
      PubmedUpdateCounters pubmedUpdateCounters) {
    int chunkSize = PubmedChunkSize.resolve(batchProperties);
    log.info("配置 pubmedUpdateArticleStep，chunk size: {}", chunkSize);

    PubmedUpdateStepListener stepListener = new PubmedUpdateStepListener(pubmedUpdateCounters);
    ExecutionContextPromotionListener promotion = new ExecutionContextPromotionListener();
    promotion.setKeys(new String[] {PubmedUpdateContextKeys.DELETED_PMIDS});

    return new StepBuilder("pubmedUpdateArticleStep", jobRepository)
        .<CanonicalPublication, PublicationSyncItem>chunk(chunkSize)
        .transactionManager(transactionManager)
        .reader(pubmedUpdateItemReader)
        .processor(pubmedUpdateRoutingProcessor)
        .writer(publicationSyncItemWriter)
        .faultTolerant()
        .skipLimit(SKIP_LIMIT)
        .skip(DataIntegrityViolationException.class)
        .skip(DuplicateKeyException.class)
        .skipListener(stepListener)
        .listener(stepListener)
        .listener(promotion)
        .build();
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

  /// 本 Step 共用的计数器。
  ///
  /// @return 计数器
  @Bean
  @StepScope
  public PubmedUpdateCounters pubmedUpdateCounters() {
    return new PubmedUpdateCounters();
  }

  /// 读取器。
  ///
  /// @param downloadUrl 文件下载地址
  /// @return 读取器
  @Bean
  @StepScope
  public PubmedUpdateItemReader pubmedUpdateItemReader(
      @Value("#{jobParameters['downloadUrl']}") String downloadUrl) {
    return new PubmedUpdateItemReader(fileDownloadPort, pubmedXmlParserPort, downloadUrl);
  }

  /// 分流处理器。
  ///
  /// @param pubmedArticleItemProcessor baseline 处理器
  /// @param scope 导入范围
  /// @param pubmedUpdateCounters 计数器
  /// @return 处理器
  @Bean
  @StepScope
  public PubmedUpdateRoutingProcessor pubmedUpdateRoutingProcessor(
      PubmedArticleItemProcessor pubmedArticleItemProcessor,
      @Value("#{jobParameters['scope']}") String scope,
      PubmedUpdateCounters pubmedUpdateCounters) {
    return new PubmedUpdateRoutingProcessor(
        publicationSyncRepository,
        pubmedArticleItemProcessor,
        PubmedUpdateScope.valueOf(scope),
        pubmedUpdateCounters);
  }

  /// 写入器。
  ///
  /// @param publicationItemWriter baseline 插入写入器
  /// @param resultMapper 结果转换
  /// @param pubmedUpdateCounters 计数器
  /// @return 写入器
  @Bean
  @StepScope
  public PublicationSyncItemWriter publicationSyncItemWriter(
      PublicationItemWriter publicationItemWriter,
      PublicationImportResultMapper resultMapper,
      PubmedUpdateCounters pubmedUpdateCounters) {
    return new PublicationSyncItemWriter(
        publicationSyncRepository,
        publicationItemWriter,
        resultMapper,
        pubmedUpdateCounters,
        clock);
  }
}
