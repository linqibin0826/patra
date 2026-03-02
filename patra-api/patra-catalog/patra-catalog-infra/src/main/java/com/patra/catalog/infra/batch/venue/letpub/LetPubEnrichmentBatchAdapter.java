package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.port.batch.LetPubEnrichmentBatchPort;
import com.patra.starter.batch.core.JobOperatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/// LetPub 富化批处理适配器。
///
/// 实现 {@link LetPubEnrichmentBatchPort}，委托 {@link JobOperatorHelper}
/// 启动 Spring Batch Job。
///
/// **时间戳策略**：使用默认的 `addTimestamp=true`，每次调度创建新 JobInstance。
/// 断点续传由 Reader 的 JPQL 过滤条件（`letpub_data IS NULL`）实现，
/// 而非 Spring Batch 的 JobInstance 重启机制。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class LetPubEnrichmentBatchAdapter implements LetPubEnrichmentBatchPort {

  private final JobOperatorHelper jobOperatorHelper;
  private final Job letPubEnrichmentJob;

  /// 构造 LetPubEnrichmentBatchAdapter。
  public LetPubEnrichmentBatchAdapter(
      JobOperatorHelper jobOperatorHelper,
      @Qualifier("letPubEnrichmentJob") Job letPubEnrichmentJob) {
    this.jobOperatorHelper = jobOperatorHelper;
    this.letPubEnrichmentJob = letPubEnrichmentJob;
  }

  /// {@inheritDoc}
  @Override
  public Long launchEnrichment() {
    log.info("启动 LetPub 期刊富化 Job");
    return jobOperatorHelper.launch(letPubEnrichmentJob, new LetPubEnrichmentJobParams());
  }
}
