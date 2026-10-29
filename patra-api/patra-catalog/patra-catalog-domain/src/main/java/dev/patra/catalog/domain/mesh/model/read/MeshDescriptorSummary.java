package dev.patra.catalog.domain.mesh.model.read;

import java.util.List;

/// MeSH 主题词摘要读模型：建议接口与内部查询共用的那组字段。
///
/// @param ui 主题词 UI（如 `D003920`）
/// @param name 主题词英文名
/// @param treeNumbers 全部树号，主树号在前，其余按字典序；没有为空列表
/// @author linqibin
/// @since 0.1.0
public record MeshDescriptorSummary(String ui, String name, List<String> treeNumbers) {

  /// 紧凑构造器：`treeNumbers` 防御性拷贝，`null` 视为空列表。
  public MeshDescriptorSummary {
    treeNumbers = treeNumbers != null ? List.copyOf(treeNumbers) : List.of();
  }

  /// 创建摘要。
  ///
  /// @param ui 主题词 UI
  /// @param name 主题词英文名
  /// @param treeNumbers 树号列表，可为 `null`
  /// @return 摘要
  public static MeshDescriptorSummary of(String ui, String name, List<String> treeNumbers) {
    return new MeshDescriptorSummary(ui, name, treeNumbers);
  }
}
