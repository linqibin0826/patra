package com.patra.catalog.infra.adapter.batch.organization;

import com.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.starter.batch.config.BatchProperties;
import com.patra.starter.batch.metrics.BatchProgressMetricsListener;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/// ROR 机构导入 Job 配置。
///
/// **Job 结构**：
///
/// ```
/// rorOrganizationImportJob
///   └── rorOrganizationImportStep (chunk-oriented)
///         ├── reader: RorOrganizationItemReader
///         └── writer: RorOrganizationItemWriter
/// ```
///
/// **配置说明**：
///
/// - chunk size 默认 500（可通过 BatchProperties 调整）
/// - 支持断点续传（RorOrganizationItemReader 实现 ItemStream）
/// - 遇到错误立即失败（不使用 FaultTolerant 模式）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
public class RorOrganizationJobConfig {

  private static final int DEFAULT_CHUNK_SIZE = 500;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final StreamingDownloadPort streamingDownloadPort;
  private final RorOrganizationParser rorOrganizationParser;
  private final RorOrganizationItemWriter rorOrganizationItemWriter;
  private final BatchProperties batchProperties;
  private final BatchProgressMetricsListener batchProgressMetricsListener;

  /// 构造函数。
  ///
  /// @param jobRepository Job 仓库
  /// @param transactionManager 事务管理器
  /// @param streamingDownloadPort 流式下载端口
  /// @param rorOrganizationParser ROR 机构解析器
  /// @param rorOrganizationItemWriter Item 写入器
  /// @param batchProperties 批处理属性
  /// @param batchProgressMetricsListener 进度指标监听器（可选，需要 MeterRegistry）
  public RorOrganizationJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      StreamingDownloadPort streamingDownloadPort,
      RorOrganizationParser rorOrganizationParser,
      RorOrganizationItemWriter rorOrganizationItemWriter,
      BatchProperties batchProperties,
      Optional<BatchProgressMetricsListener> batchProgressMetricsListener) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.streamingDownloadPort = streamingDownloadPort;
    this.rorOrganizationParser = rorOrganizationParser;
    this.rorOrganizationItemWriter = rorOrganizationItemWriter;
    this.batchProperties = batchProperties;
    this.batchProgressMetricsListener = batchProgressMetricsListener.orElse(null);
  }

  /// 配置 ROR 机构导入 Job。
  ///
  /// @return Job 实例
  @Bean
  public Job rorOrganizationImportJob() {
    return new JobBuilder("rorOrganizationImportJob", jobRepository)
        .start(rorOrganizationImportStep())
        .build();
  }

  /// 配置 ROR 机构导入 Step。
  ///
  /// @return Step 实例
  @Bean
  public Step rorOrganizationImportStep() {
    int chunkSize = getChunkSize();
    log.info("配置 rorOrganizationImportStep，chunk size: {}", chunkSize);

    var stepBuilder =
        new StepBuilder("rorOrganizationImportStep", jobRepository)
            .<OrganizationAggregate, OrganizationAggregate>chunk(chunkSize, transactionManager)
            .reader(rorOrganizationItemReader(null, null))
            .writer(rorOrganizationItemWriter);

    // 仅在指标监听器存在时注册（需要 MeterRegistry）
    if (batchProgressMetricsListener != null) {
      stepBuilder.listener(batchProgressMetricsListener);
      log.info("已注册 BatchProgressMetricsListener");
    } else {
      log.info("BatchProgressMetricsListener 未配置（需要 MeterRegistry）");
    }

    return stepBuilder.build();
  }

  /// 创建 ROR 机构 ItemReader（StepScope）。
  ///
  /// @param downloadUrl JSON 文件下载 URL（从 Job 参数注入）
  /// @param rorVersion ROR 版本号（从 Job 参数注入）
  /// @return ItemReader 实例
  @Bean
  @StepScope
  public RorOrganizationItemReader rorOrganizationItemReader(
      @Value("#{jobParameters['downloadUrl']}") String downloadUrl,
      @Value("#{jobParameters['rorVersion']}") String rorVersion) {
    return new RorOrganizationItemReader(
        streamingDownloadPort, rorOrganizationParser, downloadUrl, rorVersion);
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
