package dev.patra.catalog.domain.mesh.model.read;

/// MeSH 建议的一条候选：主题词摘要加上经哪个入口词命中。
///
/// @param descriptor 主题词摘要
/// @param matchedTerm 命中的入口词原文；命中的是主题词名本身时为 `null`
/// @author linqibin
/// @since 0.1.0
public record MeshDescriptorSuggestion(MeshDescriptorSummary descriptor, String matchedTerm) {

  /// 创建候选。
  ///
  /// @param descriptor 主题词摘要
  /// @param matchedTerm 命中的入口词原文，可为 `null`
  /// @return 候选
  public static MeshDescriptorSuggestion of(MeshDescriptorSummary descriptor, String matchedTerm) {
    return new MeshDescriptorSuggestion(descriptor, matchedTerm);
  }
}
