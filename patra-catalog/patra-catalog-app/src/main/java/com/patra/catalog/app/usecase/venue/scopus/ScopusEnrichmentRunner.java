package com.patra.catalog.app.usecase.venue.scopus;

import com.patra.catalog.app.usecase.venue.VenueEnrichRunStats;
import com.patra.catalog.app.usecase.venue.scopus.ScopusEnrichmentWorker.Outcome;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// Scopus 富化 worker loop 协调器。
///
/// 与 [com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentRunner] 结构对称，
/// 差异仅为**400ms 速率限制**——Scopus Serial Title API 免费版约 2.5 req/s 的
/// 硬限制，连续两次 [ScopusEnrichmentWorker#processVenue] 调用之间必须间隔 400ms。
///
/// **外循环职责**：
///
/// 1. 用 keyset pagination 分批读取需要富化的 venue（`lastId` 前进）
/// 2. 对每条调用 [ScopusEnrichmentWorker]（非事务）
/// 3. 两次 venue 调用之间通过 [doRateLimitSleep] 插入 400ms 停顿（首次不等）
/// 4. 捕获任何异常并计入 `failed`，保证整批 run 不中断
/// 5. 返回完整统计 [VenueEnrichRunStats]
///
/// **为什么不用独立 `RateLimiter` 类**：独立类需要 `lastCallAt` 字段，若作为
/// Spring singleton 会在跨 `run()` 调用时产生状态泄漏。本类采用 protected 方法
/// 模板 + 方法局部 `firstVenue` 标志，状态只在 `run()` 栈帧内，天然线程安全。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class ScopusEnrichmentRunner {

  /// 每批读取的 venue 数量，与 LetPub Runner 保持一致。
  private static final int BATCH_SIZE = 50;

  /// Scopus Serial Title API 免费版速率限制（约 2.5 req/s，留出余量用 400ms）。
  private static final long RATE_LIMIT_INTERVAL_MS = 400;

  private final VenueEnrichmentReadPort readPort;
  private final ScopusEnrichmentWorker worker;

  /// 执行一次完整的 Scopus 富化 run。
  ///
  /// 会一直循环直到 ReadPort 返回空列表。任何 Worker 抛出的异常都被捕获并计入
  /// `failed`，整批 run 不中断；`InterruptedException` 则恢复中断标志并立即返回。
  ///
  /// @param targetYear 目标评级年份（如 2025）
  /// @param minCitedByCount 被引次数下限（0 = 不过滤）
  /// @return 完整运行统计 [VenueEnrichRunStats]，不为 null
  public VenueEnrichRunStats run(short targetYear, int minCitedByCount) {
    log.info(
        "Scopus 富化 Runner 启动: targetYear={} minCitedByCount={} batchSize={} rateLimitMs={}",
        targetYear,
        minCitedByCount,
        BATCH_SIZE,
        RATE_LIMIT_INTERVAL_MS);

    int total = 0;
    int processed = 0;
    int skipped = 0;
    int failed = 0;
    long lastId = 0L;
    boolean firstVenue = true;

    while (true) {
      List<VenueSnapshot> batch =
          readPort.findNeedingScopusEnrichment(targetYear, minCitedByCount, lastId, BATCH_SIZE);
      if (batch.isEmpty()) {
        break;
      }

      for (VenueSnapshot v : batch) {
        total++;
        lastId = v.id();

        // 除首条外每条处理前等待 400ms 限速间隔
        if (!firstVenue) {
          try {
            doRateLimitSleep();
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn(
                "Scopus 富化 Runner 被中断（rate limit wait）: venueId={} processedSoFar={}",
                v.id(),
                processed);
            return VenueEnrichRunStats.of(total, processed, skipped, failed);
          }
        }
        firstVenue = false;

        try {
          Outcome outcome = worker.processVenue(v);
          switch (outcome) {
            case PROCESSED -> processed++;
            case MISSING_ISSN, NOT_FOUND_IN_SOURCE -> skipped++;
            default ->
                throw new IllegalStateException("Unhandled Scopus Worker Outcome: " + outcome);
          }
        } catch (Exception e) {
          // 合作式中断：检查直接抛出的 IE（理论可能）和被包装为 RuntimeException 的 IE
          // （实际常见——底层爬虫的 Thread.sleep backoff 常把 IE 包装成 RuntimeException）
          if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            log.warn("Scopus 富化 Runner 被中断: venueId={} processedSoFar={}", v.id(), processed);
            return VenueEnrichRunStats.of(total, processed, skipped, failed);
          }
          failed++;
          log.warn("Scopus 富化失败（继续）: venueId={} reason={}", v.id(), e.getMessage());
        }
      }
    }

    log.info(
        "Scopus 富化 Runner 结束: total={} processed={} skipped={} failed={}",
        total,
        processed,
        skipped,
        failed);
    return VenueEnrichRunStats.of(total, processed, skipped, failed);
  }

  /// 限速钩子：默认 `Thread.sleep(RATE_LIMIT_INTERVAL_MS)`，测试可匿名子类重写。
  ///
  /// **设计理由**：避免独立 `RateLimiter` 类作为 singleton 字段导致 `lastCallAt`
  /// 状态跨 `run()` 调用泄漏。protected 方法模板把限速行为内聚在 Runner 内部，
  /// 状态只在 `run()` 方法局部（`firstVenue` 标志），天然线程安全。
  ///
  /// @throws InterruptedException 若线程被中断，由调用方恢复 interrupt flag 并提前返回
  protected void doRateLimitSleep() throws InterruptedException {
    Thread.sleep(RATE_LIMIT_INTERVAL_MS);
  }
}
