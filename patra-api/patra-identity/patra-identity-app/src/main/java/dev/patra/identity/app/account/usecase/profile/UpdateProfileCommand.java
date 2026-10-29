package dev.patra.identity.app.account.usecase.profile;

import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.vo.profile.ProfileDraft;
import dev.patra.tool.cqrs.Command;
import java.util.Objects;

/// 当前用户整份替换自己的资料（`PUT /me/profile`）。
///
/// @param draft 原始输入
public record UpdateProfileCommand(ProfileDraft draft) implements Command<UserProfile> {

  /// 校验非空。
  public UpdateProfileCommand {
    Objects.requireNonNull(draft, "draft 不能为 null");
  }

  /// 创建命令。
  ///
  /// @param draft 原始输入
  /// @return 命令
  public static UpdateProfileCommand of(ProfileDraft draft) {
    return new UpdateProfileCommand(draft);
  }
}
