package com.patra.catalog.infra.batch.venue;

import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.starter.batch.config.BatchProperties;
import com.patra.starter.batch.metrics.BatchProgressMetricsListener;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.ItemProcessListener;
import org.springframework.batch.core.listener.ItemReadListener;
import org.springframework.batch.core.listener.ItemWriteListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/// OpenAlex Venue 导入 Job 配置。
///
/// **Job 结构**：
///
/// ```
/// venueInitializeJob
///   └── venueInitializeStep (chunk-oriented)
///         ├── reader: VenueInitializeItemReader (流式多文件顺序读取)
///         └── writer: VenueInitializeItemWriter (Upsert 策略)
/// ```
///
/// **临时文件下载特性**：
///
/// - ItemReader 按需从远程 URL 下载每个分区文件到临时目录，从本地文件解析
/// - 切换文件时自动清理当前临时文件，下载下一个分区
///
/// **配置说明**：
///
/// - chunk size 默认 500（可通过 BatchProperties 调整）
/// - 支持断点续传（VenueInitializeItemReader 实现 ItemStream，记录 fileIndex + lineIndex）
/// - 恢复时需要重新下载当前分区文件
/// - 遇到错误立即失败（不使用 FaultTolerant 模式）
///
/// **与 MeshDescriptorJobConfig 的差异**：
///
/// - Reader 使用多分区 URL 列表（逗号分隔字符串解析）
/// - 无版本号参数（OpenAlex 使用 updated_date 分区）
/// - Writer 使用 Upsert 策略（MeSH 使用纯新增）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
public class VenueInitializeJobConfig {

  private static final int DEFAULT_CHUNK_SIZE = 2000;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final FileDownloadPort fileDownloadPort;
  private final OpenAlexSourceParser openAlexSourceParser;
  private final VenueInitializeItemWriter venueInitializeItemWriter;
  private final BatchProperties batchProperties;
  private final BatchProgressMetricsListener batchProgressMetricsListener;
  private final VenueInitializeErrorListener venueInitializeErrorListener;

  /// 构造函数。
  ///
  /// @param jobRepository Job 仓库
  /// @param transactionManager 事务管理器
  /// @param fileDownloadPort 文件下载端口
  /// @param openAlexSourceParser OpenAlex Source 解析器
  /// @param venueInitializeItemWriter Item 写入器
  /// @param batchProperties 批处理属性
  /// @param batchProgressMetricsListener 进度指标监听器（可选，需要 MeterRegistry）
  /// @param venueInitializeErrorListener 错误日志监听器
  public VenueInitializeJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FileDownloadPort fileDownloadPort,
      OpenAlexSourceParser openAlexSourceParser,
      VenueInitializeItemWriter venueInitializeItemWriter,
      BatchProperties batchProperties,
      Optional<BatchProgressMetricsListener> batchProgressMetricsListener,
      VenueInitializeErrorListener venueInitializeErrorListener) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.fileDownloadPort = fileDownloadPort;
    this.openAlexSourceParser = openAlexSourceParser;
    this.venueInitializeItemWriter = venueInitializeItemWriter;
    this.batchProperties = batchProperties;
    this.batchProgressMetricsListener = batchProgressMetricsListener.orElse(null);
    this.venueInitializeErrorListener = venueInitializeErrorListener;
  }

  /// 配置 Venue 导入 Job。
  ///
  /// @return Job 实例
  @Bean
  public Job venueInitializeJob() {
    return new JobBuilder("venueInitializeJob", jobRepository).start(venueInitializeStep()).build();
  }

  /// 配置 Venue 导入 Step。
  ///
  /// @return Step 实例
  @Bean
  public Step venueInitializeStep() {
    int chunkSize = getChunkSize();
    log.info("配置 venueInitializeStep，chunk size: {}", chunkSize);

    var stepBuilder =
        new StepBuilder("venueInitializeStep", jobRepository)
            .<VenueParseResult, VenueParseResult>chunk(chunkSize)
            .transactionManager(transactionManager)
            .reader(venueInitializeItemReader(null))
            .writer(venueInitializeItemWriter);

    // 注册错误日志监听器（需要分别注册三个接口，避免方法重载歧义）
    stepBuilder.listener((ItemReadListener<VenueParseResult>) venueInitializeErrorListener);
    stepBuilder.listener(
        (ItemProcessListener<VenueParseResult, VenueParseResult>) venueInitializeErrorListener);
    stepBuilder.listener((ItemWriteListener<VenueParseResult>) venueInitializeErrorListener);
    log.info("已注册 VenueInitializeErrorListener（读取/处理/写入错误监听）");

    // 仅在指标监听器存在时注册（需要 MeterRegistry）
    if (batchProgressMetricsListener != null) {
      stepBuilder.listener(batchProgressMetricsListener);
      log.info("已注册 BatchProgressMetricsListener");
    } else {
      log.info("BatchProgressMetricsListener 未配置（需要 MeterRegistry）");
    }

    return stepBuilder.build();
  }

  /// 创建 Venue ItemReader（StepScope）。
  ///
  /// @param partitionUrls 分区 URL 列表（逗号分隔，从 Job 参数注入）
  /// @return ItemReader 实例
  @Bean
  @StepScope
  public VenueInitializeItemReader venueInitializeItemReader(
      @Value("#{jobParameters['partitionUrls']}") String partitionUrls) {
    List<String> urls = parsePartitionUrls(partitionUrls);
    log.debug("创建 VenueInitializeItemReader，分区 URL 数量: {}", urls.size());
    return new VenueInitializeItemReader(fileDownloadPort, openAlexSourceParser, urls);
  }

  /// 解析逗号分隔的分区 URL 字符串。
  ///
  /// @param partitionUrls 逗号分隔的 URL 字符串
  /// @return URL 列表
  private List<String> parsePartitionUrls(String partitionUrls) {
    if (partitionUrls == null || partitionUrls.isBlank()) {
      return List.of();
    }
    return Arrays.stream(partitionUrls.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
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
