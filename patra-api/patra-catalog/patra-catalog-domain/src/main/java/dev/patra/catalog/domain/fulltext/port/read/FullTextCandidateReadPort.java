package dev.patra.catalog.domain.fulltext.port.read;

import dev.patra.catalog.domain.fulltext.model.read.FetchCandidate;
import dev.patra.catalog.domain.fulltext.model.read.ParseCandidate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/// 全文抓取与解析的候选读端口。
///
/// @author linqibin
/// @since 0.11.0
public interface FullTextCandidateReadPort {

  /// 新文献：有 pmc 标识符、没有全文记录、未删除（`NOT EXISTS`），按 `publication_id` 升序 keyset，
  /// 每篇只出现一次（取 id 最小的那条 pmc 标识符）。
  ///
  /// @param lastPublicationId keyset 游标，从头开始传 0
  /// @param limit 批量大小
  /// @return 候选
  List<FetchCandidate> findNewFetchCandidates(long lastPublicationId, int limit);

  /// 到期：`FETCH_FAILED` 到期且未超过重试上限，或 `UNAVAILABLE`（`NOT_IN_BUCKET` / `NOT_OPEN_ACCESS`）到期。
  ///
  /// 按 `next_attempt_at` 升序、走 `(status, next_attempt_at)` 索引，不做 keyset：处理过的记录下次时间会后移
  /// 或状态改变，自然移出结果；调用方反复取到为空为止，并自行跳过本轮已处理过的。
  ///
  /// @param now 当前时间
  /// @param maxAttempts 抓取失败的重试上限
  /// @param limit 批量大小
  /// @return 候选
  List<FetchCandidate> findDueFetchCandidates(Instant now, int maxAttempts, int limit);

  /// PMCID 变了：记录里的 PMCID 与当前第一条 pmc 标识符不同，且没有设下次时间或已到期，按 keyset。
  ///
  /// @param now 当前时间
  /// @param lastPublicationId keyset 游标
  /// @param limit 批量大小
  /// @return 候选
  List<FetchCandidate> findPmcidChangedCandidates(Instant now, long lastPublicationId, int limit);

  /// 手动重抓：按文献 ID 取候选，忽略状态（仍要求有 pmc 标识符、未删除）。
  ///
  /// @param publicationIds 文献 ID
  /// @return 候选
  List<FetchCandidate> findByPublicationIds(Collection<Long> publicationIds);

  /// 一批解析候选，按 `publication_id` 升序 keyset。
  ///
  /// 候选：`FETCHED`；或 `PARSED` / `PARSE_FAILED` / `UNAVAILABLE(NO_BODY)` 且解析器版本低于当前版本。
  /// 已软删除的文献排除。
  ///
  /// @param parserVersion 当前解析器版本
  /// @param lastPublicationId keyset 游标
  /// @param limit 批量大小
  /// @return 候选
  List<ParseCandidate> findParseCandidates(int parserVersion, long lastPublicationId, int limit);
}
