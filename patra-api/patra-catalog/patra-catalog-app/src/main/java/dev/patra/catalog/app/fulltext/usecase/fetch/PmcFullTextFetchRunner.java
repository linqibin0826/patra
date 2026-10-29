package dev.patra.catalog.app.fulltext.usecase.fetch;

import dev.patra.catalog.app.fulltext.config.FullTextJobProperties;
import dev.patra.catalog.domain.fulltext.model.read.FetchCandidate;
import dev.patra.catalog.domain.fulltext.port.read.FullTextCandidateReadPort;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 全文抓取的批次调度：keyset 分批取候选，批内并发调 Worker，处理到没有候选为止。
///
/// 一篇的异常只计失败、不中断同批其他篇；线程被中断时恢复中断标志并返回已有统计。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PmcFullTextFetchRunner {

  private final FullTextCandidateReadPort candidatePort;
  private final PmcFullTextFetchWorker worker;
  private final FullTextJobProperties properties;
  private final Clock clock;

  /// 执行一次抓取：先到期的重试与再查，再 PMCID 变了的，最后新文献；每路取到为空为止。
  ///
  /// @param publicationIds 手动重抓的文献 ID；为空时处理全部到期候选
  /// @return 统计
  public FullTextFetchRunStats run(List<Long> publicationIds) {
    ExecutorService pool = Executors.newFixedThreadPool(properties.fetch().concurrency());
    try {
      if (!publicationIds.isEmpty()) {
        return processBatch(
            pool,
            candidatePort.findByPublicationIds(publicationIds),
            FullTextFetchRunStats.empty());
      }
      int batchSize = properties.fetch().batchSize();
      Set<Long> seen = new HashSet<>();
      FullTextFetchRunStats stats = FullTextFetchRunStats.empty();

      // 到期：处理过的会移出结果，不用游标；本轮已处理过的跳过，全是已处理的就停（防止持久化出错时死循环）
      while (!Thread.currentThread().isInterrupted()) {
        List<FetchCandidate> batch =
            unseen(
                candidatePort.findDueFetchCandidates(
                    clock.instant(), properties.fetch().maxAttempts(), batchSize),
                seen);
        if (batch.isEmpty()) {
          break;
        }
        stats = processBatch(pool, batch, stats);
      }
      stats =
          drainByKeyset(
              pool,
              seen,
              stats,
              last -> candidatePort.findPmcidChangedCandidates(clock.instant(), last, batchSize));
      stats =
          drainByKeyset(
              pool, seen, stats, last -> candidatePort.findNewFetchCandidates(last, batchSize));
      log.info("全文抓取完成：{}", stats);
      return stats;
    } finally {
      pool.shutdownNow();
    }
  }

  /// 按 `publication_id` 游标取到为空为止。
  private FullTextFetchRunStats drainByKeyset(
      ExecutorService pool,
      Set<Long> seen,
      FullTextFetchRunStats stats,
      LongFunction<List<FetchCandidate>> query) {
    FullTextFetchRunStats result = stats;
    long lastId = 0L;
    while (!Thread.currentThread().isInterrupted()) {
      List<FetchCandidate> page = query.apply(lastId);
      if (page.isEmpty()) {
        break;
      }
      lastId = page.getLast().publicationId();
      result = processBatch(pool, unseen(page, seen), result);
      log.info("全文抓取进度：{}，游标 {}", result, lastId);
    }
    return result;
  }

  /// 去掉本轮已处理过的，并把剩下的登记为已处理。
  private static List<FetchCandidate> unseen(List<FetchCandidate> batch, Set<Long> seen) {
    return batch.stream().filter(c -> seen.add(c.publicationId())).toList();
  }

  /// 并发处理一批，等全部完成。
  private FullTextFetchRunStats processBatch(
      ExecutorService pool, List<FetchCandidate> batch, FullTextFetchRunStats stats) {
    List<Future<FetchOutcome>> futures =
        batch.stream().map(c -> pool.submit(() -> worker.process(c))).toList();
    FullTextFetchRunStats result = stats;
    for (int i = 0; i < futures.size(); i++) {
      try {
        result = result.plus(futures.get(i).get());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return result;
      } catch (ExecutionException e) {
        log.error("全文抓取单篇异常：publicationId={}", batch.get(i).publicationId(), e.getCause());
        result = result.plus(FetchOutcome.FAILED);
      }
    }
    return result;
  }
}
