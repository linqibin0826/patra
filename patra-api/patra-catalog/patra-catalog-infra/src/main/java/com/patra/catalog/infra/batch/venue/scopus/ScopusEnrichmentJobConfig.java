package com.patra.catalog.infra.batch.venue.scopus;

import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPort;
import com.patra.catalog.infra.persistence.dao.ScopusRatingDao;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/// Scopus 期刊指标富化 Spring Batch Job 配置。
///
/// **Job 结构**：
///
/// ```
/// scopusEnrichmentJob
///   └── scopusEnrichmentStep (chunk-oriented, chunk=1)
///         ├── reader: JpaPagingItemReader (有 ISSN-L 且无 Scopus 数据的期刊)
///         ├── processor: ScopusVenueItemProcessor (API 调用 + ScopusDataMapper 映射)
///         └── writer: ScopusVenueItemWriter (→ cat_venue_scopus_rating)
/// ```
///
/// **设计要点**：
///
/// - chunk size = 1：API 限速（400ms/条），无需分组
/// - faultTolerant + skipLimit(MAX_VALUE)：单条失败跳过，不中断整体
/// - Reader 过滤条件 `NOT EXISTS`：断点续传，已有 Scopus 数据的期刊自动跳过
/// - pageSize = 50：减少数据库查询次数
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ScopusEnrichmentJobConfig {

  private static final int CHUNK_SIZE = 1;
  private static final int PAGE_SIZE = 50;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final ScopusEnrichmentPort enrichmentPort;
  private final EntityManagerFactory entityManagerFactory;
  private final ScopusRatingDao scopusRatingDao;

  /// 定义 Scopus 富化 Job。
  @Bean
  public Job scopusEnrichmentJob() {
    return new JobBuilder("scopusEnrichmentJob", jobRepository)
        .start(scopusEnrichmentStep())
        .build();
  }

  /// 定义 Scopus 富化 Step。
  ///
  /// 使用 faultTolerant 模式，单条 API 调用失败时跳过（不中断整体作业）。
  @Bean
  public Step scopusEnrichmentStep() {
    return new StepBuilder("scopusEnrichmentStep", jobRepository)
        .<VenueEntity, ScopusEnrichResult>chunk(CHUNK_SIZE)
        .transactionManager(transactionManager)
        .reader(scopusVenueItemReader())
        .processor(scopusVenueItemProcessor())
        .writer(scopusVenueItemWriter())
        .faultTolerant()
        .skipLimit(Integer.MAX_VALUE)
        .skip(Exception.class)
        .build();
  }

  /// 创建 JPA 分页 Reader。
  ///
  /// JPQL 过滤条件：
  /// - `venueType = 'JOURNAL'`：仅期刊类型
  /// - `issnL IS NOT NULL`：必须有 ISSN-L
  /// - `NOT EXISTS`：排除已有 Scopus 评级数据的期刊（断点续传）
  @Bean
  @StepScope
  public JpaPagingItemReader<VenueEntity> scopusVenueItemReader() {
    return new JpaPagingItemReaderBuilder<VenueEntity>()
        .name("scopusVenueItemReader")
        .entityManagerFactory(entityManagerFactory)
        .queryString(
            """
            SELECT v FROM VenueEntity v
            WHERE v.venueType = 'JOURNAL'
              AND v.issnL IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM ScopusRatingEntity s WHERE s.venueId = v.id)
            ORDER BY v.id
            """)
        .pageSize(PAGE_SIZE)
        .build();
  }

  /// 创建 Processor Bean。
  @Bean
  @StepScope
  public ScopusVenueItemProcessor scopusVenueItemProcessor() {
    return new ScopusVenueItemProcessor(enrichmentPort, new ScopusDataMapper());
  }

  /// 创建 Writer Bean。
  @Bean
  @StepScope
  public ScopusVenueItemWriter scopusVenueItemWriter() {
    return new ScopusVenueItemWriter(scopusRatingDao);
  }
}
