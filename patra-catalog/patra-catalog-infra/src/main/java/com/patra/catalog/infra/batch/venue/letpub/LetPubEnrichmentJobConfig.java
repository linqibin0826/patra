package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/// LetPub 期刊富化 Spring Batch Job 配置。
///
/// **Job 结构**：
///
/// ```
/// letPubEnrichmentJob
///   └── letPubEnrichmentStep (chunk-oriented, chunk=1)
///         ├── reader: JpaPagingItemReader (缺少目标年份 JCR 评级数据的期刊)
///         ├── processor: LetPubVenueItemProcessor (爬取 + LetPubDataMapper 拆解)
///         └── writer: LetPubVenueItemWriter (JCR → cat_venue_jcr_rating, CAS →
// cat_venue_cas_rating)
/// ```
///
/// **设计要点**：
///
/// - chunk size = 1：每条爬取耗时 8-10 秒，无需分组
/// - faultTolerant + skipLimit(MAX_VALUE)：单条失败跳过，不中断整体
/// - Reader 过滤条件 `NOT EXISTS` + `targetYear`：按目标年份筛选，断点续传
/// - pageSize = 50：减少数据库查询次数
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LetPubEnrichmentJobConfig {

  private static final int CHUNK_SIZE = 1;
  private static final int PAGE_SIZE = 50;

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final LetPubEnrichmentPort enrichmentPort;
  private final EntityManagerFactory entityManagerFactory;
  private final JcrRatingDao jcrRatingDao;
  private final CasRatingDao casRatingDao;

  /// 定义 LetPub 富化 Job。
  @Bean
  public Job letPubEnrichmentJob() {
    return new JobBuilder("letPubEnrichmentJob", jobRepository)
        .start(letPubEnrichmentStep())
        .build();
  }

  /// 定义 LetPub 富化 Step。
  ///
  /// 使用 faultTolerant 模式，单条爬取失败时跳过（不中断整体作业）。
  @Bean
  public Step letPubEnrichmentStep() {
    return new StepBuilder("letPubEnrichmentStep", jobRepository)
        .<VenueEntity, LetPubEnrichResult>chunk(CHUNK_SIZE)
        .transactionManager(transactionManager)
        .reader(letPubVenueItemReader(null, null))
        .processor(letPubVenueItemProcessor())
        .writer(letPubVenueItemWriter())
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
  /// - `NOT EXISTS` + `targetYear`：排除已有目标年份 JCR 评级数据的期刊（断点续传）
  /// - `minCitedByCount`：按被引次数过滤（0 = 不过滤）
  @Bean
  @StepScope
  public JpaPagingItemReader<VenueEntity> letPubVenueItemReader(
      @Value("#{jobParameters['targetYear']}") Long targetYear,
      @Value("#{jobParameters['minCitedByCount']}") Long minCitedByCount) {
    return new JpaPagingItemReaderBuilder<VenueEntity>()
        .name("letPubVenueItemReader")
        .entityManagerFactory(entityManagerFactory)
        .queryString(
            """
            SELECT v FROM VenueEntity v
            WHERE v.venueType = 'JOURNAL'
              AND v.issnL IS NOT NULL
              AND NOT EXISTS (
                SELECT 1 FROM JcrRatingEntity j
                WHERE j.venueId = v.id AND j.year = :targetYear
              )
              AND (:minCitedByCount = 0 OR v.citedByCount >= :minCitedByCount)
            ORDER BY v.id
            """)
        .parameterValues(
            Map.of(
                "targetYear", targetYear.shortValue(),
                "minCitedByCount", minCitedByCount.intValue()))
        .pageSize(PAGE_SIZE)
        .build();
  }

  /// 创建 Processor Bean。
  @Bean
  @StepScope
  public LetPubVenueItemProcessor letPubVenueItemProcessor() {
    return new LetPubVenueItemProcessor(enrichmentPort, new LetPubDataMapper());
  }

  /// 创建 Writer Bean。
  @Bean
  @StepScope
  public LetPubVenueItemWriter letPubVenueItemWriter() {
    return new LetPubVenueItemWriter(jcrRatingDao, casRatingDao);
  }
}
