package dev.linqibin.patra.catalog.infra.batch.mesh;

import com.patra.starter.batch.config.BatchProperties;
import com.patra.starter.batch.metrics.BatchProgressMetricsListener;
import dev.linqibin.patra.catalog.domain.model.aggregate.MeshScrAggregate;
import dev.linqibin.patra.catalog.domain.port.parser.MeshScrParserPort;
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

/// MeSH 补充概念记录（SCR）导入 Job 配置。
///
/// **Job 结构**：
///
/// ```
/// meshScrImportJob
///   └── meshScrImportStep (chunk-oriented)
///         ├── reader: MeshScrItemReader
///         └── writer: MeshScrItemWriter
/// ```
///
/// **配置说明**：
///
/// - chunk size 默认 500（可通过 BatchProperties 调整）
/// - 支持断点续传（MeshScrItemReader 实现 ItemStream）
/// - 遇到错误立即失败（不使用 FaultTolerant 模式）
///
/// **数据量说明**：
///
/// MeSH SCR 文件包含约 350,000 条记录，是 Descriptor 的 10 倍，
/// 建议使用较大的 chunk size（500-1000）以提高处理效率。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
public class MeshScrJobConfig {

  private static final int DEFAULT_CHUNK_SIZE = 500;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final FileDownloadPort fileDownloadPort;
  private final MeshScrParserPort scrParserPort;
  private final MeshScrItemWriter meshScrItemWriter;
  private final BatchProperties batchProperties;
  private final MeshImportJobExecutionListener meshImportJobExecutionListener;
  private final BatchProgressMetricsListener batchProgressMetricsListener;

  /// 构造函数。
  ///
  /// @param jobRepository Job 仓库
  /// @param transactionManager 事务管理器
  /// @param fileDownloadPort 文件下载端口
  /// @param scrParserPort SCR 解析端口
  /// @param meshScrItemWriter Item 写入器
  /// @param batchProperties 批处理属性
  /// @param meshImportJobExecutionListener Job 执行监听器
  /// @param batchProgressMetricsListener 进度指标监听器（可选，需要 MeterRegistry）
  public MeshScrJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FileDownloadPort fileDownloadPort,
      MeshScrParserPort scrParserPort,
      MeshScrItemWriter meshScrItemWriter,
      BatchProperties batchProperties,
      MeshImportJobExecutionListener meshImportJobExecutionListener,
      Optional<BatchProgressMetricsListener> batchProgressMetricsListener) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.fileDownloadPort = fileDownloadPort;
    this.scrParserPort = scrParserPort;
    this.meshScrItemWriter = meshScrItemWriter;
    this.batchProperties = batchProperties;
    this.meshImportJobExecutionListener = meshImportJobExecutionListener;
    this.batchProgressMetricsListener = batchProgressMetricsListener.orElse(null);
  }

  /// 配置 MeSH SCR 导入 Job。
  ///
  /// @return Job 实例
  @Bean
  public Job meshScrImportJob() {
    return new JobBuilder("meshScrImportJob", jobRepository)
        .listener(meshImportJobExecutionListener)
        .start(meshScrImportStep())
        .build();
  }

  /// 配置 MeSH SCR 导入 Step。
  ///
  /// @return Step 实例
  @Bean
  public Step meshScrImportStep() {
    int chunkSize = getChunkSize();
    log.info("配置 meshScrImportStep，chunk size: {}", chunkSize);

    var stepBuilder =
        new StepBuilder("meshScrImportStep", jobRepository)
            .<MeshScrAggregate, MeshScrAggregate>chunk(chunkSize)
            .transactionManager(transactionManager)
            .reader(meshScrItemReader(null, null))
            .writer(meshScrItemWriter);

    // 仅在指标监听器存在时注册（需要 MeterRegistry）
    if (batchProgressMetricsListener != null) {
      stepBuilder.listener(batchProgressMetricsListener);
      log.info("已注册 BatchProgressMetricsListener");
    } else {
      log.info("BatchProgressMetricsListener 未配置（需要 MeterRegistry）");
    }

    return stepBuilder.build();
  }

  /// 创建 MeSH SCR ItemReader（StepScope）。
  ///
  /// @param downloadUrl XML 文件下载 URL（从 Job 参数注入）
  /// @param meshVersion MeSH 版本号（从 Job 参数注入）
  /// @return ItemReader 实例
  @Bean
  @StepScope
  public MeshScrItemReader meshScrItemReader(
      @Value("#{jobParameters['downloadUrl']}") String downloadUrl,
      @Value("#{jobParameters['meshVersion']}") String meshVersion) {
    return new MeshScrItemReader(fileDownloadPort, scrParserPort, downloadUrl, meshVersion);
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
