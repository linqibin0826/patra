package dev.patra.catalog.infra.venue.persistence;

/// 按 NLM 解析的投影：在 [VenueResolveRow] 之外带上命中的 NLM（可能是副 NLM）。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueNlmMatchRow extends VenueResolveRow {

  /// 命中的 NLM（请求里的键，主或副）。
  ///
  /// @return 命中的 NLM
  String getMatchedNlmId();
}
