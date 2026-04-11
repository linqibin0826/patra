package com.patra.catalog.domain.port.enrichment;

import java.util.Objects;

/// Venue 工作队列读端的轻量投影。
///
/// 用于 [VenueEnrichmentReadPort] 返回"需要富化的 venue"列表，
/// 只包含 Runner/Worker 实际需要的字段：`id`（keyset 游标 + 持久化目标）
/// 和 `issnL`（富化查询键）。
///
/// @param id 聚合根主键，非 null
/// @param issnL ISSN-L，若缺失则调用方应跳过该 venue
/// @author linqibin
/// @since 0.1.0
public record VenueSnapshot(Long id, String issnL) {

  public VenueSnapshot {
    Objects.requireNonNull(id, "VenueSnapshot.id 不可为 null");
  }

  /// 创建 [VenueSnapshot]。
  ///
  /// @param id 聚合根主键，不可为 null（由紧凑构造器校验）
  /// @param issnL ISSN-L，允许为 null
  public static VenueSnapshot of(Long id, String issnL) {
    return new VenueSnapshot(id, issnL);
  }
}
