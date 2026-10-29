package dev.patra.identity.domain.account.model.vo.profile;

import java.util.Objects;

/// 一个关注领域：MeSH 主题词 UI 加英文名快照。
///
/// @param ui 主题词 UI
/// @param name catalog 给的英文名快照
public record ResearchInterest(MeshUi ui, String name) {

  /// 校验非空。
  public ResearchInterest {
    Objects.requireNonNull(ui, "ui 不能为 null");
    Objects.requireNonNull(name, "name 不能为 null");
  }

  /// 工厂方法。
  ///
  /// @param ui 主题词 UI
  /// @param name 英文名快照
  /// @return 关注领域
  public static ResearchInterest of(MeshUi ui, String name) {
    return new ResearchInterest(ui, name);
  }
}
