package com.patra.catalog.app.usecase.venue.letpub;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentRunner.RunStats;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichCommand;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichResult;
import com.patra.common.cqrs.CommandHandler;
import com.patra.common.error.ApplicationException;
import com.patra.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// LetPub 期刊富化命令处理器。
///
/// 委托 [LetPubEnrichmentRunner] 同步执行 worker loop，返回完整的运行统计。
///
/// **事务说明**：本方法**不使用** `@Transactional`——外层循环非事务，
/// 事务边界由 [LetPubEnrichmentWorker#processVenue] 的 `REQUIRES_NEW` 管理。
///
/// **异常处理**：[DomainException] 与 [ApplicationException] 直接传播，
/// 其他 [RuntimeException] 包装为 `ApplicationException(CAT_1302)` 统一返回。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueLetPubEnrichHandler
    implements CommandHandler<VenueLetPubEnrichCommand, VenueLetPubEnrichResult> {

  private final LetPubEnrichmentRunner runner;

  /// 同步执行完整 LetPub 富化 worker loop，返回运行统计。
  ///
  /// 阻塞直到 [LetPubEnrichmentRunner] 处理完所有候选 venue。
  /// 统计信息由 `RunStats` 直接映射为 [VenueLetPubEnrichResult]。
  ///
  /// @param command 富化参数：目标年份 + 被引次数下限
  /// @return 同步执行结果，包含 totalRead / processed / skipped / failed 四项统计
  /// @throws com.patra.common.error.DomainException 领域异常直接传播
  /// @throws com.patra.common.error.ApplicationException 业务异常直接传播，
  ///     未知 `RuntimeException` 被包装为 `ApplicationException(CAT_1302)`
  @Override
  public VenueLetPubEnrichResult handle(VenueLetPubEnrichCommand command) {
    log.info(
        "启动 LetPub 富化 Handler: targetYear={} minCitedByCount={}",
        command.targetYear(),
        command.minCitedByCount());
    try {
      RunStats stats = runner.run(command.targetYear(), command.minCitedByCount());
      log.info(
          "LetPub 富化完成: total={} processed={} skipped={} failed={}",
          stats.totalRead(),
          stats.processed(),
          stats.skipped(),
          stats.failed());
      return VenueLetPubEnrichResult.of(
          stats.totalRead(), stats.processed(), stats.skipped(), stats.failed());
    } catch (DomainException | ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1302, "LetPub 期刊富化失败: " + e.getMessage(), e);
    }
  }
}
