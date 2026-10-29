package dev.patra.identity.domain.account.model.read;

import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.policy.AvatarPalette;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;

/// 批量用户摘要要的原始数据：用户表加资料表的几列，不读子表（PAP-108）。对外输出的是 `viewFor` 按状态与访客
/// 处理后的 `UserSummaryView`。
///
/// @param userId 用户 ID
/// @param username 用户名
/// @param displayName 资料里的昵称原值，没设时为 `null`（回退在 `viewFor` 里做，已注销不回退）
/// @param avatar 头像，没上传时为 `null`
/// @param status 状态
/// @param emailVerified 邮箱是否已验证
/// @param profileVisibility 主页可见性
@Builder
public record UserSummaryReadModel(
    long userId,
    String username,
    String displayName,
    Avatar avatar,
    UserStatus status,
    boolean emailVerified,
    ProfileVisibility profileVisibility) {

  /// 校验非空。
  public UserSummaryReadModel {
    Objects.requireNonNull(username, "username 不能为 null");
    Objects.requireNonNull(status, "status 不能为 null");
    Objects.requireNonNull(profileVisibility, "profileVisibility 不能为 null");
  }

  /// 主页可见性规则。
  ///
  /// @return 规则
  public ProfileAccess access() {
    return ProfileAccess.of(userId, status, emailVerified, profileVisibility);
  }

  /// 按状态与访客出摘要：已注销先返回占位（不带用户名、不回退昵称）；已封禁不给头像；其余昵称没设时回退成用户名。
  ///
  /// @param viewerId 访客的用户 ID，匿名时为空
  /// @return 摘要
  public UserSummaryView viewFor(Optional<Long> viewerId) {
    UserSummaryView.UserSummaryViewBuilder view =
        UserSummaryView.builder()
            .userId(userId)
            .avatarPalette(AvatarPalette.of(userId))
            .status(status);
    if (status == UserStatus.DELETED) {
      return view.emailVerified(false).profileVisible(false).build();
    }
    return view.username(username)
        .displayName(displayName == null ? username : displayName)
        .avatar(status.servesAvatar() ? avatar : null)
        .emailVerified(emailVerified)
        .profileVisible(access().isVisibleTo(viewerId))
        .build();
  }
}
