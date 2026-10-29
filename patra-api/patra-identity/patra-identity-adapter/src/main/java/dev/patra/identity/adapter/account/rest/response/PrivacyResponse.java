package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;

/// `GET /me/privacy` 与 `PUT /me/privacy` 的响应。
///
/// @param profileVisibility 主页可见性
/// @param professionalVisibility 专业背景可见性
/// @param searchIndexing 是否允许搜索引擎收录
public record PrivacyResponse(
    String profileVisibility, String professionalVisibility, boolean searchIndexing) {

  /// 从值对象映射。
  ///
  /// @param privacy 隐私设置
  /// @return 响应
  public static PrivacyResponse from(PrivacySettings privacy) {
    return new PrivacyResponse(
        privacy.profile().name(), privacy.professional().name(), privacy.searchIndexing());
  }
}
