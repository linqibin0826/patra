package com.patra.catalog.domain.port.read;

import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import java.util.List;

/// Venue 富化工作队列读端口。
///
/// 为同步 worker loop 提供"待富化 venue"的分页读取，使用 **keyset pagination**
/// （`id > lastId`）配合 `NOT EXISTS` 过滤：
///
/// - `NOT EXISTS` 动态收敛 — 随 Worker 写入评级数据，下一批次的候选集合
///   自动缩减，无需专门的"失败清单"表或重试队列
/// - `lastId` 游标前进 — 避免同一 venue 失败后被无限重捞（同一次 run 内失败
///   的 venue 留待下次 Job 调度时再次进入查询）
///
/// @author linqibin
/// @since 0.1.0
public interface VenueEnrichmentReadPort {

  /// 查询缺少指定年份 JCR 评级的期刊 venue。
  ///
  /// @param targetYear 目标年份（对应 `cat_venue_jcr_rating.year`）
  /// @param minCitedByCount 被引次数下限（0 = 不过滤）
  /// @param lastId keyset 游标，传 0 表示从头开始
  /// @param limit 批大小（通常 50）
  /// @return 按 `id` 升序排列的 venue 快照列表；空列表表示没有更多候选
  List<VenueSnapshot> findNeedingLetPubEnrichment(
      short targetYear, int minCitedByCount, long lastId, int limit);

  /// 查询缺少指定年份 Scopus 评级的期刊 venue。
  ///
  /// 与 [findNeedingLetPubEnrichment] 结构一致，但 `NOT EXISTS` 子查询
  /// 指向 `cat_venue_scopus_rating` 表。
  ///
  /// @param targetYear 目标年份（对应 `cat_venue_scopus_rating.year`）
  /// @param minCitedByCount 被引次数下限（0 = 不过滤）
  /// @param lastId keyset 游标，传 0 表示从头开始
  /// @param limit 批大小（通常 50）
  /// @return 按 `id` 升序排列的 venue 快照列表；空列表表示没有更多候选
  List<VenueSnapshot> findNeedingScopusEnrichment(
      short targetYear, int minCitedByCount, long lastId, int limit);

  /// 读取指定 venue 已有的封面对象键。
  ///
  /// 用于 [LetPubEnrichmentWorker] 判断是否需要重新下载封面（幂等跳过）。
  /// 放在 ReadPort 而不是 PersistPort，是因为这是一次纯读取，语义上属于
  /// "venue 工作队列的附加元数据查询"。
  ///
  /// @param venueId 目标 venue 主键
  /// @return 封面对象键；若 venue 无封面返回 empty
  java.util.Optional<String> findExistingCoverKey(long venueId);
}
