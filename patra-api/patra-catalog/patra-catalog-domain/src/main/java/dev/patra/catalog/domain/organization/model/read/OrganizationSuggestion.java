package dev.patra.catalog.domain.organization.model.read;

/// 机构建议的一条候选：机构摘要加上命中的那条名称。
///
/// @param organization 机构摘要
/// @param matchedName 命中查询的名称原文（显示名、中文名、别名或缩写）
/// @author linqibin
/// @since 0.1.0
public record OrganizationSuggestion(OrganizationSummary organization, String matchedName) {

  /// 创建候选。
  ///
  /// @param organization 机构摘要
  /// @param matchedName 命中的名称原文
  /// @return 候选
  public static OrganizationSuggestion of(OrganizationSummary organization, String matchedName) {
    return new OrganizationSuggestion(organization, matchedName);
  }
}
