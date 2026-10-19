package dev.patra.catalog.app.usecase.fulltext.fetch;

import dev.patra.catalog.app.usecase.fulltext.FullTextJobProperties;
import dev.patra.catalog.domain.model.read.fulltext.FetchCandidate;
import dev.patra.catalog.domain.port.read.FullTextCandidateReadPort;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

  /// 执行一次抓取。
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
      FullTextFetchRunStats stats = FullTextFetchRunStats.empty();
      long lastId = 0L;
      while (!Thread.currentThread().isInterrupted()) {
        List<FetchCandidate> batch =
            candidatePort.findFetchCandidates(
                clock.instant(),
                properties.fetch().maxAttempts(),
                lastId,
                properties.fetch().batchSize());
        if (batch.isEmpty()) {
          break;
        }
        stats = processBatch(pool, batch, stats);
        lastId = batch.getLast().publicationId();
        log.info("全文抓取进度：{}，游标 {}", stats, lastId);
      }
      return stats;
    } finally {
      pool.shutdownNow();
    }
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
