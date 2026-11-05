package dev.patra.catalog.api.publication.dto;

/// 同主题邻居的来源。
///
/// @author linqibin
/// @since 0.1.0
public enum SubjectNeighborReasonResp {

  /// 主要主题词相同。
  SAME_MESH,

  /// 同一本期刊。
  SAME_VENUE
}
