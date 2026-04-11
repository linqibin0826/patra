package com.patra.catalog.domain.port.enrichment;

/// Scopus 富化数据持久化端口。
///
/// 实现类负责：
///
/// 1. 调用 `ScopusDataMapper` 把 [ScopusVenueData] 展开成多行 `ScopusRatingEntity`
/// 2. 对每条按 `(venue_id, year)` 去重过滤（防 UK 冲突）
/// 3. 通过 `ScopusRatingDao` 批量插入剩余行
///
/// **事务约束**：本方法**不自带事务**。调用方必须是 App 层承载
/// `@Transactional(REQUIRES_NEW)` 边界的 Persister bean（如 `ScopusEnrichmentPersister`），
/// 依赖跨 bean 代理激活事务。
///
/// **无封面处理**：Scopus Serial Title API 不提供期刊封面，
/// 故与 [LetPubEnrichmentPersistPort] 相比缺少 `coverObjectKey` 参数。
///
/// @author linqibin
/// @since 0.1.0
public interface ScopusEnrichmentPersistPort {

  /// 持久化一个 venue 的 Scopus 富化结果。
  ///
  /// @param venueId 目标 venue 主键，不可为 null
  /// @param data Scopus API 返回的原始数据，不可为 null
  /// @return 插入统计（便于日志和测试断言），不为 null
  PersistStats persist(long venueId, ScopusVenueData data);

  /// Scopus 持久化的插入统计结果。
  ///
  /// @param scopusRatingsInserted 去重后实际插入 `cat_venue_scopus_rating` 的行数
  record PersistStats(int scopusRatingsInserted) {

    /// 创建 [PersistStats]。
    ///
    /// @param inserted 插入的行数
    public static PersistStats of(int inserted) {
      return new PersistStats(inserted);
    }
  }
}
