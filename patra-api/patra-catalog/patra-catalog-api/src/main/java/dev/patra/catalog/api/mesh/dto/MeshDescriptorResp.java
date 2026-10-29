package dev.patra.catalog.api.mesh.dto;

import java.util.List;

/// MeSH 主题词摘要响应 DTO，暴露给下游服务（identity）。
///
/// @param ui 主题词 UI（如 `D003920`）
/// @param name 主题词英文名（关注领域的名称快照）
/// @param treeNumbers 全部树号，主树号在前
/// @author linqibin
/// @since 0.1.0
public record MeshDescriptorResp(String ui, String name, List<String> treeNumbers) {

  /// 紧凑构造器：`treeNumbers` 防御性拷贝，`null` 视为空列表。
  public MeshDescriptorResp {
    treeNumbers = treeNumbers != null ? List.copyOf(treeNumbers) : List.of();
  }

  /// 创建响应。
  ///
  /// @param ui 主题词 UI
  /// @param name 主题词英文名
  /// @param treeNumbers 树号列表
  /// @return 响应
  public static MeshDescriptorResp of(String ui, String name, List<String> treeNumbers) {
    return new MeshDescriptorResp(ui, name, treeNumbers);
  }
}
