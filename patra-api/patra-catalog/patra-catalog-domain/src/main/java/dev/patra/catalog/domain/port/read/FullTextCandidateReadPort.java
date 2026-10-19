package dev.patra.catalog.domain.port.read;

import dev.patra.catalog.domain.model.read.fulltext.FetchCandidate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/// 全文抓取与解析的候选读端口。
///
/// @author linqibin
/// @since 0.11.0
public interface FullTextCandidateReadPort {

  /// 一批抓取候选（工程 spec 第 5.2 节四类的并集），按 `publication_id` 升序 keyset，每篇只出现一次。
  ///
  /// 四类：有 pmc 标识符且没有全文记录；`FETCH_FAILED` 到期且未超过重试上限；记录里的 PMCID 与当前标识符不同；
  /// `UNAVAILABLE`（`NOT_IN_BUCKET` / `NOT_OPEN_ACCESS`）到期。已软删除的文献排除。
  ///
  /// @param now 当前时间
  /// @param maxAttempts 抓取失败的重试上限
  /// @param lastPublicationId keyset 游标，从头开始传 0
  /// @param limit 批量大小
  /// @return 候选
  List<FetchCandidate> findFetchCandidates(
      Instant now, int maxAttempts, long lastPublicationId, int limit);

  /// 手动重抓：按文献 ID 取候选，忽略状态（仍要求有 pmc 标识符、未删除）。
  ///
  /// @param publicationIds 文献 ID
  /// @return 候选
  List<FetchCandidate> findByPublicationIds(Collection<Long> publicationIds);
}
