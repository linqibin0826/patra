package com.patra.catalog.infra.batch;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.port.XmlParserPort;
import com.patra.catalog.infra.batch.listener.MeshImportJobExecutionListener;
import com.patra.starter.batch.config.BatchProperties;
import lombok.RequiredArgsConstructor;
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
import org.springframework.dao.DataIntegrityViolationException;
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
/// - 支持跳过 DataIntegrityViolationException（最多 100 条）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MeshDescriptorJobConfig {

  private static final int DEFAULT_CHUNK_SIZE = 500;
  private static final int SKIP_LIMIT = 100;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final XmlParserPort xmlParserPort;
  private final MeshDescriptorItemWriter meshDescriptorItemWriter;
  private final BatchProperties batchProperties;
  private final MeshImportJobExecutionListener meshImportJobExecutionListener;

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

    return new StepBuilder("meshDescriptorImportStep", jobRepository)
        .<MeshDescriptorAggregate, MeshDescriptorAggregate>chunk(chunkSize, transactionManager)
        .reader(meshDescriptorItemReader(null, null))
        .writer(meshDescriptorItemWriter)
        .faultTolerant()
        .skipLimit(SKIP_LIMIT)
        .skip(DataIntegrityViolationException.class)
        .build();
  }

  /// 创建 MeSH 主题词 ItemReader（StepScope）。
  ///
  /// @param filePath XML 文件路径（从 Job 参数注入）
  /// @param meshVersion MeSH 版本号（从 Job 参数注入）
  /// @return ItemReader 实例
  @Bean
  @StepScope
  public MeshDescriptorItemReader meshDescriptorItemReader(
      @Value("#{jobParameters['filePath']}") String filePath,
      @Value("#{jobParameters['meshVersion']}") String meshVersion) {
    return new MeshDescriptorItemReader(xmlParserPort, filePath, meshVersion);
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
