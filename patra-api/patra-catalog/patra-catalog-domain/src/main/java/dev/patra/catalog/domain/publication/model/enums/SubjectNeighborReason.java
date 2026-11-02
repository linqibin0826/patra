package dev.patra.catalog.domain.publication.model.enums;

/// 同主题邻居的来源：先同主要主题词，再同期刊（analytics「看过还看了」的补位）。
///
/// @author linqibin
/// @since 0.1.0
public enum SubjectNeighborReason {

  /// 主要主题词相同（两边都把它当主要主题词）。
  SAME_MESH,

  /// 同一本期刊。
  SAME_VENUE
}
