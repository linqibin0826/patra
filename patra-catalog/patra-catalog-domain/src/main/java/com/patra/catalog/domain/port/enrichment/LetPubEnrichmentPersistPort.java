package com.patra.catalog.domain.port.enrichment;

/// LetPub 富化数据持久化端口。
///
/// 实现类负责：
///
/// 1. 调用 `LetPubDataMapper` 把 [LetPubVenueData] 展开成 JCR/CAS/CasWarning 多行
/// 2. 对每种实体做 `(venue_id, year[:edition])` 去重过滤（防 UK 冲突）
/// 3. 通过对应 DAO 执行批量插入
/// 4. 若 `coverObjectKey` 非 null，UPDATE `cat_venue.image_object_key`
///
/// **事务约束**：本方法**不自带事务**。调用方必须是 App 层承载
/// `@Transactional(REQUIRES_NEW)` 边界的 Persister bean（如 `LetPubEnrichmentPersister`），
/// 依赖跨 bean 代理激活事务。
///
/// @author linqibin
/// @since 0.1.0
public interface LetPubEnrichmentPersistPort {

  /// 持久化一个 venue 的 LetPub 富化结果。
  ///
  /// @param venueId 目标 venue 主键，不可为 null
  /// @param data LetPub 爬取到的原始数据，不可为 null
  /// @param coverObjectKey 新下载的封面对象键；**允许为 null**
  ///     （null 表示本轮未下载或 venue 已存在封面不需重下，不更新 image_object_key 列）
  /// @return 插入统计（便于日志和测试断言），不为 null
  PersistStats persist(long venueId, LetPubVenueData data, String coverObjectKey);

  /// LetPub 持久化的插入统计结果。
  ///
  /// @param jcrInserted 本次真正插入 `cat_venue_jcr_rating` 的行数（去重后）
  /// @param casInserted 本次真正插入 `cat_venue_cas_rating` 的行数（去重后）
  /// @param warningInserted 本次真正插入 `cat_venue_cas_warning` 的行数（去重后）
  /// @param coverUpdated 是否更新了 `cat_venue.image_object_key` 列
  record PersistStats(int jcrInserted, int casInserted, int warningInserted, boolean coverUpdated) {

    /// 创建 [PersistStats]。
    ///
    /// @param jcr 插入的 JCR 行数
    /// @param cas 插入的 CAS 评级行数
    /// @param warning 插入的 CAS 预警行数
    /// @param cover 是否更新了封面对象键
    public static PersistStats of(int jcr, int cas, int warning, boolean cover) {
      return new PersistStats(jcr, cas, warning, cover);
    }
  }
}
