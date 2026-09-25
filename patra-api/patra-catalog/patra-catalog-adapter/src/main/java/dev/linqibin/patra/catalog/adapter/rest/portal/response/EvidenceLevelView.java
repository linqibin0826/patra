package dev.linqibin.patra.catalog.adapter.rest.portal.response;

import dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel;

/// 证据等级视图，文献列表与详情共用。
///
/// @param level 等级名称（[EvidenceLevel] 枚举名）
/// @param rank 等级权重
/// @param label 中文展示标签
/// @param derived 是否已成功衍生（非 UNKNOWN）
/// @author linqibin
/// @since 0.1.0
public record EvidenceLevelView(String level, int rank, String label, boolean derived) {

  /// 由领域枚举创建视图。
  ///
  /// @param level 证据等级
  /// @return 视图
  public static EvidenceLevelView of(EvidenceLevel level) {
    return new EvidenceLevelView(level.name(), level.rank(), level.label(), level.isDerived());
  }
}
