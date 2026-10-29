package dev.patra.catalog.domain.publication.port.repository;

import dev.patra.catalog.domain.publication.model.vo.PublicationCompleteData;
import dev.patra.catalog.domain.publication.model.vo.PublicationSourceRow;
import dev.patra.catalog.domain.publication.model.vo.PublicationSyncTarget;
import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import dev.patra.catalog.domain.publication.model.vo.SourcePosition;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// PubMed 增量同步的覆盖更新仓储。
///
/// 原则：内部 ID 不变；PubMed 管的部分全部替换；别的流程写的字段（`is_oa`、`oa_status`、
/// `citation_count`、`ext_data`、`record_remarks`、metadata 的 `has_full_text` / `full_text_url`、
/// 非 PubMed 来源的标识符、OA 位置、全文两表）保持原值。
///
/// @author linqibin
/// @since 0.11.0
public interface PublicationSyncRepository {

  /// 按 PMID 查库里的文献，包含已软删除的行。
  ///
  /// @param pmids PMID
  /// @return PMID → 同步目标，查不到的 PMID 不在结果里
  Map<String, PublicationSyncTarget> findSyncTargetsByPmids(Collection<String> pmids);

  /// 按 DOI 原值精确查文献 ID，包含已软删除的行（与 `uk_doi` 同口径：区分大小写、不看删除标记）。
  ///
  /// @param dois DOI（调用方已 trim）
  /// @return 库里的 DOI 原值 → 文献 ID
  Map<String, Long> findPublicationIdsByDois(Collection<String> dois);

  /// 覆盖更新已有文献及其 PubMed 子数据。
  ///
  /// @param data 覆盖数据，已分配好已有 ID
  /// @param doiOverrides 文献 ID → 要写入的 DOI（新 DOI 冲突时保留旧值）
  /// @param syncedAt 本次同步时间
  /// @param refreshSyncedAt 要刷新 `last_synced_at` 的文献 ID（拿到真正更新的版本时）；其余保留原值
  void overwriteAllWithAssociations(
      List<PublicationCompleteData> data,
      Map<Long, String> doiOverrides,
      Instant syncedAt,
      Set<Long> refreshSyncedAt);

  /// 按 PMID 写来源行（存在则覆盖）。
  ///
  /// @param rows 来源行
  void upsertSources(List<PublicationSourceRow> rows);

  /// 按 `DeleteCitation` 软删文献，带来源位置守卫。
  ///
  /// - 库里有该 PMID 且来源表的位置不比删除文件新（或没有位置）：软删（已软删的不重复计数），来源位置改为删除文件
  /// - 来源表的位置比删除文件新：不动
  /// - 库里没有该 PMID：`writeTombstones` 为真时写墓碑来源行（原文为空），否则不动
  ///
  /// @param pmids 被删除的 PMID
  /// @param position 删除文件的来源位置
  /// @param writeTombstones 是否为库里没有的 PMID 写墓碑（`ALL` 范围）
  /// @param deletedAt 删除时间
  /// @return 本次实际软删的行数
  int softDeleteByPmids(
      Collection<String> pmids,
      SourcePosition position,
      boolean writeTombstones,
      Instant deletedAt);

  /// 本地重算的候选：有原文、文献未软删的来源行，按 PMID 升序键集分页。
  ///
  /// @param afterPmid 上一页最后一个 PMID；第一页为 null
  /// @param limit 每页条数
  /// @return 原文与来源位置
  List<PubmedSourceRecord> findReparseCandidates(String afterPmid, int limit);
}
