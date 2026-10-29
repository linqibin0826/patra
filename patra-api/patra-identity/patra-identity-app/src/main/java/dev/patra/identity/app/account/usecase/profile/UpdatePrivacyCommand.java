package dev.patra.identity.app.account.usecase.profile;

import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.tool.cqrs.Command;

/// 当前用户改隐私设置（`PUT /me/privacy`），三项都必填，原始输入由 `ProfilePolicy.parsePrivacy` 解析。
///
/// @param profileVisibility 主页可见性
/// @param professionalVisibility 专业背景可见性
/// @param searchIndexing 是否允许搜索引擎收录
public record UpdatePrivacyCommand(
    String profileVisibility, String professionalVisibility, Boolean searchIndexing)
    implements Command<PrivacySettings> {

  /// 创建命令。
  ///
  /// @param profileVisibility 主页可见性
  /// @param professionalVisibility 专业背景可见性
  /// @param searchIndexing 是否允许收录
  /// @return 命令
  public static UpdatePrivacyCommand of(
      String profileVisibility, String professionalVisibility, Boolean searchIndexing) {
    return new UpdatePrivacyCommand(profileVisibility, professionalVisibility, searchIndexing);
  }
}
