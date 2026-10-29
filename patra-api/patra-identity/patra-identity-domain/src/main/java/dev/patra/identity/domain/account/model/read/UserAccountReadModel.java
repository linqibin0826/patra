package dev.patra.identity.domain.account.model.read;

import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.policy.AvatarPalette;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 「当前用户」接口和登录、注册响应要的账号信息：用户表和资料表拼起来。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱，已注销时为 `null`
/// @param emailVerified 邮箱是否已验证
/// @param username 用户名
/// @param displayName 昵称；资料里没设时已回退成用户名，永远非空
/// @param avatar 头像，没上传时为 `null`
/// @param status 状态
/// @param deletionDueAt 注销到期时间，不在冷静期时为 `null`
/// @param nextUsernameChangeAt 下次可以改名的时间，没改过名时为 `null`
@Builder
public record UserAccountReadModel(
    long userId,
    String email,
    boolean emailVerified,
    String username,
    String displayName,
    Avatar avatar,
    UserStatus status,
    Instant deletionDueAt,
    Instant nextUsernameChangeAt) {

  /// 校验非空。
  public UserAccountReadModel {
    Objects.requireNonNull(username, "username 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(status, "status 不能为 null");
  }

  /// 默认头像色号，按用户 ID 算。
  ///
  /// @return 0–11
  public int avatarPalette() {
    return AvatarPalette.of(userId);
  }
}
