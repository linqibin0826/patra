package com.patra.catalog.app.usecase.venue.letpub;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichCommand;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichResult;
import com.patra.catalog.domain.port.batch.LetPubEnrichmentBatchPort;
import com.patra.common.cqrs.CommandHandler;
import com.patra.common.error.ApplicationException;
import com.patra.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// LetPub 期刊富化命令处理器。
///
/// 委托 {@link LetPubEnrichmentBatchPort} 启动 Spring Batch 富化作业。
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
public class VenueLetPubEnrichHandler
    implements CommandHandler<VenueLetPubEnrichCommand, VenueLetPubEnrichResult> {

  private final LetPubEnrichmentBatchPort letPubEnrichmentBatchPort;

  /// 处理 LetPub 期刊富化命令。
  ///
  /// 启动批处理作业，返回 Job Execution ID 供追踪。
  @Override
  public VenueLetPubEnrichResult handle(VenueLetPubEnrichCommand command) {
    log.info("启动 LetPub 期刊富化任务");

    try {
      Long executionId = letPubEnrichmentBatchPort.launchEnrichment();
      log.info("LetPub 期刊富化任务已启动，executionId：{}", executionId);
      return VenueLetPubEnrichResult.of(executionId);

    } catch (DomainException | ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1302, "LetPub 期刊富化失败: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1302, "LetPub 期刊富化时发生意外错误: " + e.getMessage(), e);
    }
  }
}
