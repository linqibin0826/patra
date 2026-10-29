package dev.patra.identity.domain.account.model.read;

import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import java.util.Objects;
import java.util.Optional;

/// 主页可见性规则，全仓只有这一份：公开主页（`PublicUserReadModel`）与批量用户摘要（`UserSummaryReadModel`）
/// 都委托它，interaction 等调用方只拿结果，不自己实现。
///
/// @param ownerId 主页主人的用户 ID
/// @param status 主人的账号状态
/// @param emailVerified 主人的邮箱是否已验证
/// @param visibility 主人设的主页可见性
public record ProfileAccess(
    long ownerId, UserStatus status, boolean emailVerified, ProfileVisibility visibility) {

  /// 校验非空。
  public ProfileAccess {
    Objects.requireNonNull(status, "status 不能为 null");
    Objects.requireNonNull(visibility, "visibility 不能为 null");
  }

  /// 工厂方法。
  ///
  /// @param ownerId 主人的用户 ID
  /// @param status 账号状态
  /// @param emailVerified 邮箱是否已验证
  /// @param visibility 主页可见性
  /// @return 规则
  public static ProfileAccess of(
      long ownerId, UserStatus status, boolean emailVerified, ProfileVisibility visibility) {
    return new ProfileAccess(ownerId, status, emailVerified, visibility);
  }

  /// 实际能被谁看到：账号不是 `ACTIVE` 或邮箱未验证时只有本人；否则按用户设的主页可见性。
  ///
  /// @return 可见范围
  public PublicAudience audience() {
    if (status != UserStatus.ACTIVE || !emailVerified) {
      return PublicAudience.SELF;
    }
    return switch (visibility) {
      case PUBLIC -> PublicAudience.PUBLIC;
      case SIGNED_IN -> PublicAudience.SIGNED_IN;
      case SELF -> PublicAudience.SELF;
    };
  }

  /// 请求者能不能看到：已注销的对任何人都不可见（含本人，先于本人判断）；本人永远能看；`PUBLIC` 谁都能看；
  /// `SIGNED_IN` 要有请求者。
  ///
  /// @param requesterId 请求者的用户 ID，匿名时为空
  /// @return 能看到时为 `true`
  public boolean isVisibleTo(Optional<Long> requesterId) {
    if (status == UserStatus.DELETED) {
      return false;
    }
    if (isOwner(requesterId)) {
      return true;
    }
    return switch (audience()) {
      case PUBLIC -> true;
      case SIGNED_IN -> requesterId.isPresent();
      case SELF -> false;
    };
  }

  /// 请求者是不是主人。
  ///
  /// @param requesterId 请求者
  /// @return 是主人时为 `true`
  public boolean isOwner(Optional<Long> requesterId) {
    return requesterId.filter(id -> id == ownerId).isPresent();
  }
}
