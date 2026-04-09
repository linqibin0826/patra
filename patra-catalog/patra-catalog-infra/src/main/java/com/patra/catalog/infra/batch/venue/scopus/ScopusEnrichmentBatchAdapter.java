package com.patra.catalog.infra.batch.venue.scopus;

import com.patra.catalog.domain.port.batch.ScopusEnrichmentBatchPort;
import com.patra.catalog.infra.batch.venue.VenueEnrichmentJobParams;
import com.patra.starter.batch.core.JobOperatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/// Scopus 富化批处理适配器。
///
/// 实现 {@link ScopusEnrichmentBatchPort}，委托 {@link JobOperatorHelper}
/// 启动 Spring Batch Job。
///
/// **时间戳策略**：使用默认的 `addTimestamp=true`，每次调度创建新 JobInstance。
/// 断点续传由 Reader 的 `NOT EXISTS` 子查询（基于目标年份）实现，
/// 而非 Spring Batch 的 JobInstance 重启机制。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class ScopusEnrichmentBatchAdapter implements ScopusEnrichmentBatchPort {

  private final JobOperatorHelper jobOperatorHelper;
  private final Job scopusEnrichmentJob;

  /// 构造 ScopusEnrichmentBatchAdapter。
  public ScopusEnrichmentBatchAdapter(
      JobOperatorHelper jobOperatorHelper,
      @Qualifier("scopusEnrichmentJob") Job scopusEnrichmentJob) {
    this.jobOperatorHelper = jobOperatorHelper;
    this.scopusEnrichmentJob = scopusEnrichmentJob;
  }

  /// {@inheritDoc}
  @Override
  public Long launchEnrichment(short targetYear, int minCitedByCount) {
    log.info("启动 Scopus 期刊指标富化 Job，targetYear={}, minCitedByCount={}", targetYear, minCitedByCount);
    return jobOperatorHelper.launch(
        scopusEnrichmentJob,
        new VenueEnrichmentJobParams((long) targetYear, (long) minCitedByCount));
  }
}
