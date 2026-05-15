package dev.linqibin.patra.catalog.domain.port.enrichment;

import java.util.Objects;

/// Venue 工作队列读端的轻量投影。
///
/// 用于 [VenueEnrichmentReadPort] 返回"需要富化的 venue"列表，
/// 包含 Runner/Worker 实际需要的字段：
///
/// - `id`：聚合根主键，同时作为 keyset 游标和持久化目标
/// - `issnL`：富化查询键（空 → Worker 跳过）
/// - `existingCoverKey`：venue 当前封面对象键（空 → LetPub Worker 需要下载新封面）
///
/// **为什么在这一层投影封面键**：LetPub 富化流程里，判断"是否需要下载新封面"的
/// 逻辑和"是否需要抓取 venue"属于同一次查询的两个产物——与其在 Worker 里再往
/// `cat_venue` 多发一次 PK 查询读 `image_object_key`，不如一次 JPQL projection
/// 把两者都拿回来。Scopus 管线无封面概念，字段在 Scopus 结果中恒为 null，开销
/// 只是多一列投影，无实际成本。
///
/// @param id 聚合根主键，非 null
/// @param issnL ISSN-L，若缺失则调用方应跳过该 venue
/// @param existingCoverKey venue 当前封面对象键，无封面时为 null
/// @author linqibin
/// @since 0.1.0
public record VenueSnapshot(Long id, String issnL, String existingCoverKey) {

  public VenueSnapshot {
    Objects.requireNonNull(id, "VenueSnapshot.id 不可为 null");
  }

  /// 创建 [VenueSnapshot]。
  ///
  /// @param id 聚合根主键，不可为 null（由紧凑构造器校验）
  /// @param issnL ISSN-L，允许为 null
  /// @param existingCoverKey 封面对象键，允许为 null（无封面 / Scopus 管线）
  /// @return 新建的 [VenueSnapshot] 实例
  public static VenueSnapshot of(Long id, String issnL, String existingCoverKey) {
    return new VenueSnapshot(id, issnL, existingCoverKey);
  }
}
