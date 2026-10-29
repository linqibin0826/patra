package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.domain.account.model.vo.profile.ResearchInterest;

/// 一个关注领域。
///
/// @param ui MeSH 主题词 UI
/// @param name 英文名快照
public record ResearchInterestResponse(String ui, String name) {

  /// 从值对象映射。
  ///
  /// @param interest 关注领域
  /// @return 响应
  public static ResearchInterestResponse from(ResearchInterest interest) {
    return new ResearchInterestResponse(interest.ui().value(), interest.name());
  }
}
