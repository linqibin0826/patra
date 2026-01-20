package com.patra.catalog.infra.adapter.batch.mesh;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.port.parser.MeshDescriptorParserPort;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
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

/// MeSH 主题词导入 Job 配置。
///
/// **Job 结构**：
///
/// ```
/// meshDescriptorImportJob
///   └── meshDescriptorImportStep (chunk-oriented)
///         ├── reader: MeshDescriptorItemReader
///         └── writer: MeshDescriptorItemWriter
/// ```
///
/// **配置说明**：
///
/// - chunk size 默认 500（可通过 BatchProperties 调整）
/// - 支持断点续传（MeshDescriptorItemReader 实现 ItemStream）
/// - 遇到错误立即失败（不使用 FaultTolerant 模式）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
public class MeshDescriptorJobConfig {

  private static final int DEFAULT_CHUNK_SIZE = 500;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final StreamingDownloadPort streamingDownloadPort;
  private final MeshDescriptorParserPort descriptorParserPort;
  private final MeshDescriptorItemWriter meshDescriptorItemWriter;
  private final BatchProperties batchProperties;
  private final MeshImportJobExecutionListener meshImportJobExecutionListener;
  private final BatchProgressMetricsListener batchProgressMetricsListener;

  /// 构造函数。
  ///
  /// @param jobRepository Job 仓库
  /// @param transactionManager 事务管理器
  /// @param streamingDownloadPort 流式下载端口
  /// @param descriptorParserPort 主题词解析端口
  /// @param meshDescriptorItemWriter Item 写入器
  /// @param batchProperties 批处理属性
  /// @param meshImportJobExecutionListener Job 执行监听器
  /// @param batchProgressMetricsListener 进度指标监听器（可选，需要 MeterRegistry）
  public MeshDescriptorJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      StreamingDownloadPort streamingDownloadPort,
      MeshDescriptorParserPort descriptorParserPort,
      MeshDescriptorItemWriter meshDescriptorItemWriter,
      BatchProperties batchProperties,
      MeshImportJobExecutionListener meshImportJobExecutionListener,
      Optional<BatchProgressMetricsListener> batchProgressMetricsListener) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.streamingDownloadPort = streamingDownloadPort;
    this.descriptorParserPort = descriptorParserPort;
    this.meshDescriptorItemWriter = meshDescriptorItemWriter;
    this.batchProperties = batchProperties;
    this.meshImportJobExecutionListener = meshImportJobExecutionListener;
    this.batchProgressMetricsListener = batchProgressMetricsListener.orElse(null);
  }

  /// 配置 MeSH 主题词导入 Job。
  ///
  /// @return Job 实例
  @Bean
  public Job meshDescriptorImportJob() {
    return new JobBuilder("meshDescriptorImportJob", jobRepository)
        .listener(meshImportJobExecutionListener)
        .start(meshDescriptorImportStep())
        .build();
  }

  /// 配置 MeSH 主题词导入 Step。
  ///
  /// @return Step 实例
  @Bean
  public Step meshDescriptorImportStep() {
    int chunkSize = getChunkSize();
    log.info("配置 meshDescriptorImportStep，chunk size: {}", chunkSize);

    var stepBuilder =
        new StepBuilder("meshDescriptorImportStep", jobRepository)
            .<MeshDescriptorAggregate, MeshDescriptorAggregate>chunk(chunkSize)
            .transactionManager(transactionManager)
            .reader(meshDescriptorItemReader(null, null))
            .writer(meshDescriptorItemWriter);

    // 仅在指标监听器存在时注册（需要 MeterRegistry）
    if (batchProgressMetricsListener != null) {
      stepBuilder.listener(batchProgressMetricsListener);
      log.info("已注册 BatchProgressMetricsListener");
    } else {
      log.info("BatchProgressMetricsListener 未配置（需要 MeterRegistry）");
    }

    return stepBuilder.build();
  }

  /// 创建 MeSH 主题词 ItemReader（StepScope）。
  ///
  /// @param downloadUrl XML 文件下载 URL（从 Job 参数注入）
  /// @param meshVersion MeSH 版本号（从 Job 参数注入）
  /// @return ItemReader 实例
  @Bean
  @StepScope
  public MeshDescriptorItemReader meshDescriptorItemReader(
      @Value("#{jobParameters['downloadUrl']}") String downloadUrl,
      @Value("#{jobParameters['meshVersion']}") String meshVersion) {
    return new MeshDescriptorItemReader(
        streamingDownloadPort, descriptorParserPort, downloadUrl, meshVersion);
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
