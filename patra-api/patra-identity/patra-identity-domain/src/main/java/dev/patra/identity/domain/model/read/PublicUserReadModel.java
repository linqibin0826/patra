package dev.patra.identity.domain.model.read;

import dev.patra.identity.domain.model.enums.UserStatus;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;

/// 公开身份卡要的信息。`userId` 和 `status` 只用来判可见性，不进响应。
///
/// @param userId 用户 ID
/// @param username 用户名
/// @param displayName 昵称；资料里没设时已回退成用户名
/// @param status 状态
/// @param emailVerified 邮箱是否已验证
@Builder
public record PublicUserReadModel(
    long userId, String username, String displayName, UserStatus status, boolean emailVerified) {

  /// 校验非空。
  public PublicUserReadModel {
    Objects.requireNonNull(username, "username 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(status, "status 不能为 null");
  }

  /// 能被谁看到：账号正常且邮箱已验证是 `PUBLIC`，否则只有本人。
  ///
  /// @return 可见范围
  public PublicAudience audience() {
    return status == UserStatus.ACTIVE && emailVerified
        ? PublicAudience.PUBLIC
        : PublicAudience.SELF;
  }

  /// 请求者能不能看到：谁都能看，或者请求者就是本人。
  ///
  /// @param requesterId 请求者的用户 ID，匿名时为空
  /// @return 能看到时为 `true`
  public boolean isVisibleTo(Optional<Long> requesterId) {
    return audience() == PublicAudience.PUBLIC
        || requesterId.filter(id -> id == userId).isPresent();
  }
}
