package com.patra.catalog.app.usecase.venue.letpub;

import com.patra.catalog.app.usecase.venue.VenueEnrichRunStats;
import com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentWorker.Outcome;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// LetPub 富化 worker loop 协调器。
///
/// **外循环职责**：
///
/// 1. 用 keyset pagination 分批读取需要富化的 venue（`lastId` 前进）
/// 2. 对每条调用 [LetPubEnrichmentWorker]（运行在独立 REQUIRES_NEW 事务里）
/// 3. 捕获任何异常并计入 `failed`，保证整批 run 不中断
/// 4. 返回完整统计 [VenueEnrichRunStats]
///
/// **为什么不用 @Transactional**：外循环跨 batch 和 venue，事务边界应由
/// [LetPubEnrichmentWorker] 在 venue 粒度管理。Runner 本身是纯循环协调，
/// 若加 `@Transactional` 会与 Worker 的 `REQUIRES_NEW` 产生嵌套事务语义，
/// 反而破坏单 venue 隔离。
///
/// **Keyset 游标的必要性**：`NOT EXISTS` 守卫让成功 venue 自动从下一批候选集消失，
/// 但失败的 venue 依然匹配查询——若无 `id > :lastId` 游标，失败 venue 会被无限重捞
/// 形成死循环。游标前进确保当次 run 不重捞，下次 Job 调度时 `lastId=0` 重置自愈。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class LetPubEnrichmentRunner {

  /// 每批读取的 venue 数量（keyset pagination 页大小）。
  private static final int BATCH_SIZE = 50;

  private final VenueEnrichmentReadPort readPort;
  private final LetPubEnrichmentWorker worker;

  /// 执行一次完整的 LetPub 富化 run。
  ///
  /// 会一直循环直到 ReadPort 返回空列表（所有候选 venue 已处理或失败跳过）。
  /// 任何 Worker 抛出的异常都被捕获并计入 `failed`，整批 run 不中断。
  ///
  /// @param targetYear 目标评级年份（如 2025）
  /// @param minCitedByCount 被引次数下限（0 = 不过滤）
  /// @return 完整运行统计 [VenueEnrichRunStats]，不为 null
  public VenueEnrichRunStats run(short targetYear, int minCitedByCount) {
    log.info(
        "LetPub 富化 Runner 启动: targetYear={} minCitedByCount={} batchSize={}",
        targetYear,
        minCitedByCount,
        BATCH_SIZE);

    int total = 0;
    int processed = 0;
    int skipped = 0;
    int failed = 0;
    long lastId = 0L;

    while (true) {
      List<VenueSnapshot> batch =
          readPort.findNeedingLetPubEnrichment(targetYear, minCitedByCount, lastId, BATCH_SIZE);
      if (batch.isEmpty()) {
        break;
      }

      for (VenueSnapshot v : batch) {
        total++;
        lastId = v.id();
        try {
          Outcome outcome = worker.processVenue(v);
          switch (outcome) {
            case PROCESSED -> processed++;
            case MISSING_ISSN, NOT_FOUND_IN_SOURCE -> skipped++;
            default ->
                throw new IllegalStateException("Unhandled LetPub Worker Outcome: " + outcome);
          }
        } catch (Exception e) {
          // 合作式中断：恢复中断标志并立即返回当前统计，不再处理剩余 venue。
          // 注：worker.processVenue 声明里没有 throws InterruptedException，但底层爬虫
          // 的 Thread.sleep backoff 可能把 InterruptedException 包装为 RuntimeException
          // 抛出，所以同时检查直接 IE 和 cause 链里的 IE。
          if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            log.warn("LetPub 富化 Runner 被中断: venueId={} processedSoFar={}", v.id(), processed);
            return VenueEnrichRunStats.of(total, processed, skipped, failed);
          }
          failed++;
          log.warn("LetPub 富化失败（继续）: venueId={} reason={}", v.id(), e.getMessage());
        }
      }
    }

    log.info(
        "LetPub 富化 Runner 结束: total={} processed={} skipped={} failed={}",
        total,
        processed,
        skipped,
        failed);
    return VenueEnrichRunStats.of(total, processed, skipped, failed);
  }
}
