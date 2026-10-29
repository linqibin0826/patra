package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.domain.account.model.vo.profile.Affiliation;

/// 机构。
///
/// @param rorId 裸 ROR ID；自由文本时为 `null`
/// @param name 名称：有 ROR ID 时是 catalog 的快照，否则是手填的自由文本
public record OrganizationResponse(String rorId, String name) {

  /// 从值对象映射。
  ///
  /// @param affiliation 机构，可为 `null`
  /// @return 响应；没有机构时为 `null`
  public static OrganizationResponse from(Affiliation affiliation) {
    return switch (affiliation) {
      case null -> null;
      case Affiliation.Registered registered ->
          new OrganizationResponse(registered.rorId().value(), registered.name());
      case Affiliation.FreeText freeText -> new OrganizationResponse(null, freeText.name());
    };
  }
}
