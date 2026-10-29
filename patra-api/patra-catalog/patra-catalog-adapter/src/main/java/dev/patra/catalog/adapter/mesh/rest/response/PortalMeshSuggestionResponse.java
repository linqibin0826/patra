package dev.patra.catalog.adapter.mesh.rest.response;

import java.util.List;

/// MeSH 建议的一条候选。
///
/// @param ui 主题词 UI
/// @param name 主题词英文名
/// @param matchedTerm 经入口词命中时的入口词原文，命中主题词名本身时为 `null`
/// @param treeNumbers 全部树号，主树号在前
/// @author linqibin
/// @since 0.1.0
public record PortalMeshSuggestionResponse(
    String ui, String name, String matchedTerm, List<String> treeNumbers) {

  /// 紧凑构造器：`treeNumbers` 防御性拷贝，`null` 视为空列表。
  public PortalMeshSuggestionResponse {
    treeNumbers = treeNumbers != null ? List.copyOf(treeNumbers) : List.of();
  }

  /// 创建候选。
  ///
  /// @param ui 主题词 UI
  /// @param name 主题词英文名
  /// @param matchedTerm 入口词原文，可为 `null`
  /// @param treeNumbers 树号列表
  /// @return 候选
  public static PortalMeshSuggestionResponse of(
      String ui, String name, String matchedTerm, List<String> treeNumbers) {
    return new PortalMeshSuggestionResponse(ui, name, matchedTerm, treeNumbers);
  }
}
