package dev.patra.catalog.domain.port.repository;

import dev.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import dev.patra.catalog.domain.model.vo.publication.PublicationSyncTarget;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
  /// 前置条件：每条 `data.getPublicationId()` 已是库里存在的 ID。已软删除的会被复活。
  ///
  /// @param data 覆盖数据，已分配好已有 ID
  /// @param doiOverrides 文献 ID → 要写入的 DOI（DOI 冲突时调用方传旧值），不在其中的用新值
  /// @param syncedAt 本次同步时间，写入 `last_synced_at`
  void overwriteAllWithAssociations(
      List<PublicationCompleteData> data, Map<Long, String> doiOverrides, Instant syncedAt);

  /// 按 PMID 软删除文献，已删除的不重复计数。
  ///
  /// @param pmids PMID
  /// @param deletedAt 删除时间
  /// @return 本次实际软删除的行数
  int softDeleteByPmids(Collection<String> pmids, Instant deletedAt);
}
