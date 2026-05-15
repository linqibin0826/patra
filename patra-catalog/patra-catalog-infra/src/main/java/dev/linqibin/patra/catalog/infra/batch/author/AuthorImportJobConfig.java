package dev.linqibin.patra.catalog.infra.batch.author;

import com.patra.starter.batch.config.BatchProperties;
import com.patra.starter.batch.metrics.BatchProgressMetricsListener;
import dev.linqibin.patra.catalog.domain.model.aggregate.AuthorAggregate;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
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

/// PubMed Computed Authors 导入 Job 配置。
///
/// **Job 结构**：
///
/// ```
/// authorImportJob
///   └── authorImportStep (chunk-oriented)
///         ├── reader: AuthorItemReader
///         └── writer: AuthorItemWriter
/// ```
///
/// **配置说明**：
///
/// - chunk size 默认 1000（可通过 BatchProperties 调整）
/// - 支持断点续传（AuthorItemReader 实现 ItemStream）
/// - 遇到错误立即失败（不使用 FaultTolerant 模式）
///
/// **性能考量**：
///
/// - 数据量约 2100 万条，chunk size 5000（平衡内存与事务开销）
/// - 处理速率预计 3000-8000 条/秒，总耗时约 1-2 小时
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
public class AuthorImportJobConfig {

  private static final int DEFAULT_CHUNK_SIZE = 5000;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final FileDownloadPort fileDownloadPort;
  private final PubMedComputedAuthorParser parser;
  private final AuthorItemWriter authorItemWriter;
  private final BatchProperties batchProperties;
  private final AuthorImportJobExecutionListener authorImportJobExecutionListener;
  private final BatchProgressMetricsListener batchProgressMetricsListener;

  /// 构造函数。
  ///
  /// @param jobRepository Job 仓库
  /// @param transactionManager 事务管理器
  /// @param fileDownloadPort 文件下载端口
  /// @param parser JSON Lines 解析器
  /// @param authorItemWriter Item 写入器
  /// @param batchProperties 批处理属性
  /// @param authorImportJobExecutionListener Job 执行监听器
  /// @param batchProgressMetricsListener 进度指标监听器（可选，需要 MeterRegistry）
  public AuthorImportJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FileDownloadPort fileDownloadPort,
      PubMedComputedAuthorParser parser,
      AuthorItemWriter authorItemWriter,
      BatchProperties batchProperties,
      AuthorImportJobExecutionListener authorImportJobExecutionListener,
      Optional<BatchProgressMetricsListener> batchProgressMetricsListener) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.fileDownloadPort = fileDownloadPort;
    this.parser = parser;
    this.authorItemWriter = authorItemWriter;
    this.batchProperties = batchProperties;
    this.authorImportJobExecutionListener = authorImportJobExecutionListener;
    this.batchProgressMetricsListener = batchProgressMetricsListener.orElse(null);
  }

  /// 配置 PubMed Computed Authors 导入 Job。
  ///
  /// @return Job 实例
  @Bean
  public Job authorImportJob() {
    return new JobBuilder("authorImportJob", jobRepository)
        .listener(authorImportJobExecutionListener)
        .start(authorImportStep())
        .build();
  }

  /// 配置 PubMed Computed Authors 导入 Step。
  ///
  /// @return Step 实例
  @Bean
  public Step authorImportStep() {
    int chunkSize = getChunkSize();
    log.info("配置 authorImportStep，chunk size: {}", chunkSize);

    var stepBuilder =
        new StepBuilder("authorImportStep", jobRepository)
            .<AuthorAggregate, AuthorAggregate>chunk(chunkSize)
            .transactionManager(transactionManager)
            .reader(authorItemReader(null))
            .writer(authorItemWriter);

    // 仅在指标监听器存在时注册（需要 MeterRegistry）
    if (batchProgressMetricsListener != null) {
      stepBuilder.listener(batchProgressMetricsListener);
      log.info("已注册 BatchProgressMetricsListener");
    } else {
      log.info("BatchProgressMetricsListener 未配置（需要 MeterRegistry）");
    }

    return stepBuilder.build();
  }

  /// 创建 PubMed Computed Authors ItemReader（StepScope）。
  ///
  /// @param downloadUrl JSON Lines 文件下载 URL（从 Job 参数注入）
  /// @return ItemReader 实例
  @Bean
  @StepScope
  public AuthorItemReader authorItemReader(
      @Value("#{jobParameters['downloadUrl']}") String downloadUrl) {
    // 获取最大记录数配置
    Long maxRecords = getMaxRecords();
    return new AuthorItemReader(fileDownloadPort, parser, downloadUrl, maxRecords);
  }

  /// 获取最大导入记录数限制。
  ///
  /// @return 最大记录数，如果未配置或不限制则返回 null
  private Long getMaxRecords() {
    if (batchProperties != null
        && batchProperties.getImportLimit() != null
        && batchProperties.getImportLimit().hasLimit()) {
      return batchProperties.getImportLimit().getMaxRecords();
    }
    return null;
  }

  /// 获取 chunk size。
  ///
  /// 优先使用 Job 专属的 `DEFAULT_CHUNK_SIZE`（5000），
  /// 仅当全局配置值更大时才使用配置值（允许调大，不允许调小）。
  ///
  /// @return chunk size
  private int getChunkSize() {
    if (batchProperties != null && batchProperties.getChunk() != null) {
      int configuredSize = batchProperties.getChunk().getDefaultSize();
      if (configuredSize > DEFAULT_CHUNK_SIZE) {
        return configuredSize;
      }
    }
    return DEFAULT_CHUNK_SIZE;
  }
}
