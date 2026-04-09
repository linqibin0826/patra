package com.patra.catalog.app.usecase.venue.scopus;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.venue.scopus.command.VenueScopusEnrichCommand;
import com.patra.catalog.app.usecase.venue.scopus.command.VenueScopusEnrichResult;
import com.patra.catalog.domain.port.batch.ScopusEnrichmentBatchPort;
import com.patra.common.cqrs.CommandHandler;
import com.patra.common.error.ApplicationException;
import com.patra.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// Scopus 期刊指标富化命令处理器。
///
/// 委托 {@link ScopusEnrichmentBatchPort} 启动 Spring Batch 富化作业。
///
/// **事务说明**：
///
/// 本方法**不使用 @Transactional**——仅启动 Spring Batch Job，
/// 实际持久化由 Job 的 chunk 事务管理（chunk size = 1）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueScopusEnrichHandler
    implements CommandHandler<VenueScopusEnrichCommand, VenueScopusEnrichResult> {

  private final ScopusEnrichmentBatchPort scopusEnrichmentBatchPort;

  /// 处理 Scopus 期刊指标富化命令。
  ///
  /// 启动批处理作业，返回 Job Execution ID 供追踪。
  @Override
  public VenueScopusEnrichResult handle(VenueScopusEnrichCommand command) {
    log.info(
        "启动 Scopus 期刊指标富化任务，targetYear={}, minCitedByCount={}",
        command.targetYear(),
        command.minCitedByCount());

    try {
      Long executionId =
          scopusEnrichmentBatchPort.launchEnrichment(
              command.targetYear(), command.minCitedByCount());
      log.info("Scopus 期刊指标富化任务已启动，executionId：{}", executionId);
      return VenueScopusEnrichResult.of(executionId);

    } catch (DomainException | ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1303, "Scopus 期刊富化失败: " + e.getMessage(), e);
    }
  }
}
