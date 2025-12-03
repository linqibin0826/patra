package com.patra.catalog.infra.batch.venue;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.infra.batch.openalex.OpenAlexSourceParser;
import com.patra.starter.batch.config.BatchProperties;
import com.patra.starter.batch.metrics.BatchProgressMetricsListener;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.ItemWriteListener;
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

/// OpenAlex Venue 导入 Job 配置。
///
/// **Job 结构**：
///
/// ```
/// venueImportJob
///   └── venueImportStep (chunk-oriented)
///         ├── reader: VenueImportItemReader (多文件顺序读取)
///         └── writer: VenueImportItemWriter (Upsert 策略)
/// ```
///
/// **配置说明**：
///
/// - chunk size 默认 500（可通过 BatchProperties 调整）
/// - 支持断点续传（VenueImportItemReader 实现 ItemStream，记录 fileIndex + lineIndex）
/// - 遇到错误立即失败（不使用 FaultTolerant 模式）
///
/// **与 MeshDescriptorJobConfig 的差异**：
///
/// - Reader 使用多文件路径列表（逗号分隔字符串解析）
/// - 无版本号参数（OpenAlex 使用 updated_date 分区）
/// - Writer 使用 Upsert 策略（MeSH 使用纯新增）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
public class VenueImportJobConfig {

  private static final int DEFAULT_CHUNK_SIZE = 500;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final OpenAlexSourceParser openAlexSourceParser;
  private final VenueImportItemWriter venueImportItemWriter;
  private final BatchProperties batchProperties;
  private final BatchProgressMetricsListener batchProgressMetricsListener;
  private final VenueImportErrorListener venueImportErrorListener;

  /// 构造函数。
  ///
  /// @param jobRepository Job 仓库
  /// @param transactionManager 事务管理器
  /// @param openAlexSourceParser OpenAlex Source 解析器
  /// @param venueImportItemWriter Item 写入器
  /// @param batchProperties 批处理属性
  /// @param batchProgressMetricsListener 进度指标监听器（可选，需要 MeterRegistry）
  /// @param venueImportErrorListener 错误日志监听器
  public VenueImportJobConfig(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      OpenAlexSourceParser openAlexSourceParser,
      VenueImportItemWriter venueImportItemWriter,
      BatchProperties batchProperties,
      Optional<BatchProgressMetricsListener> batchProgressMetricsListener,
      VenueImportErrorListener venueImportErrorListener) {
    this.jobRepository = jobRepository;
    this.transactionManager = transactionManager;
    this.openAlexSourceParser = openAlexSourceParser;
    this.venueImportItemWriter = venueImportItemWriter;
    this.batchProperties = batchProperties;
    this.batchProgressMetricsListener = batchProgressMetricsListener.orElse(null);
    this.venueImportErrorListener = venueImportErrorListener;
  }

  /// 配置 Venue 导入 Job。
  ///
  /// @return Job 实例
  @Bean
  public Job venueImportJob() {
    return new JobBuilder("venueImportJob", jobRepository).start(venueImportStep()).build();
  }

  /// 配置 Venue 导入 Step。
  ///
  /// @return Step 实例
  @Bean
  public Step venueImportStep() {
    int chunkSize = getChunkSize();
    log.info("配置 venueImportStep，chunk size: {}", chunkSize);

    var stepBuilder =
        new StepBuilder("venueImportStep", jobRepository)
            .<VenueAggregate, VenueAggregate>chunk(chunkSize, transactionManager)
            .reader(venueImportItemReader(null))
            .writer(venueImportItemWriter);

    // 注册错误日志监听器（需要分别注册三个接口，避免方法重载歧义）
    stepBuilder.listener((ItemReadListener<VenueAggregate>) venueImportErrorListener);
    stepBuilder.listener(
        (ItemProcessListener<VenueAggregate, VenueAggregate>) venueImportErrorListener);
    stepBuilder.listener((ItemWriteListener<VenueAggregate>) venueImportErrorListener);
    log.info("已注册 VenueImportErrorListener（读取/处理/写入错误监听）");

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
  /// @param filePaths 文件路径列表（逗号分隔，从 Job 参数注入）
  /// @return ItemReader 实例
  @Bean
  @StepScope
  public VenueImportItemReader venueImportItemReader(
      @Value("#{jobParameters['filePaths']}") String filePaths) {
    List<Path> paths = parseFilePaths(filePaths);
    log.debug("创建 VenueImportItemReader，文件数量: {}", paths.size());
    return new VenueImportItemReader(openAlexSourceParser, paths);
  }

  /// 解析逗号分隔的文件路径字符串为 Path 列表。
  ///
  /// @param filePaths 逗号分隔的路径字符串
  /// @return Path 列表
  private List<Path> parseFilePaths(String filePaths) {
    if (filePaths == null || filePaths.isBlank()) {
      return List.of();
    }
    return Arrays.stream(filePaths.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(Path::of)
        .collect(Collectors.toList());
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
