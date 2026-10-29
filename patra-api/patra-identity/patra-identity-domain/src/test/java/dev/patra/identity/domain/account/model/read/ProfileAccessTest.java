package dev.patra.identity.domain.account.model.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// ProfileAccess 单元测试：主页可见性规则的唯一实现（PAP-108 spec 第 4 节）。
class ProfileAccessTest {

  private static final long OWNER = 7L;
  private static final Optional<Long> ANONYMOUS = Optional.empty();
  private static final Optional<Long> OTHER = Optional.of(99L);
  private static final Optional<Long> SELF = Optional.of(OWNER);

  @Test
  void should_be_self_only_when_unverified_or_not_active() {
    assertThat(access(UserStatus.ACTIVE, false, ProfileVisibility.PUBLIC).audience())
        .isEqualTo(PublicAudience.SELF);
    for (UserStatus status :
        List.of(UserStatus.PENDING_DELETION, UserStatus.BANNED, UserStatus.DELETED)) {
      assertThat(access(status, true, ProfileVisibility.PUBLIC).audience())
          .isEqualTo(PublicAudience.SELF);
    }
  }

  @Test
  void should_follow_profile_visibility_when_active_and_verified() {
    assertThat(active(ProfileVisibility.PUBLIC).audience()).isEqualTo(PublicAudience.PUBLIC);
    assertThat(active(ProfileVisibility.SIGNED_IN).audience()).isEqualTo(PublicAudience.SIGNED_IN);
    assertThat(active(ProfileVisibility.SELF).audience()).isEqualTo(PublicAudience.SELF);
  }

  @Test
  void should_be_visible_according_to_matrix() {
    assertVisibility(active(ProfileVisibility.PUBLIC), true, true, true);
    assertVisibility(active(ProfileVisibility.SIGNED_IN), false, true, true);
    assertVisibility(active(ProfileVisibility.SELF), false, false, true);
    assertVisibility(
        access(UserStatus.ACTIVE, false, ProfileVisibility.PUBLIC), false, false, true);
    assertVisibility(
        access(UserStatus.PENDING_DELETION, true, ProfileVisibility.PUBLIC), false, false, true);
    assertVisibility(access(UserStatus.BANNED, true, ProfileVisibility.PUBLIC), false, false, true);
  }

  @Test
  void should_hide_deleted_account_even_from_owner() {
    assertVisibility(
        access(UserStatus.DELETED, true, ProfileVisibility.PUBLIC), false, false, false);
  }

  /// 正常且邮箱已验证。
  ///
  /// @param visibility 主页可见性
  /// @return 规则
  private static ProfileAccess active(ProfileVisibility visibility) {
    return access(UserStatus.ACTIVE, true, visibility);
  }

  /// 构造规则。
  ///
  /// @param status 状态
  /// @param emailVerified 邮箱是否已验证
  /// @param visibility 主页可见性
  /// @return 规则
  private static ProfileAccess access(
      UserStatus status, boolean emailVerified, ProfileVisibility visibility) {
    return ProfileAccess.of(OWNER, status, emailVerified, visibility);
  }

  /// 断言匿名、他人、本人三种请求者能否看到。
  ///
  /// @param access 规则
  /// @param anonymous 匿名能看到
  /// @param other 他人能看到
  /// @param self 本人能看到
  private static void assertVisibility(
      ProfileAccess access, boolean anonymous, boolean other, boolean self) {
    assertThat(access.isVisibleTo(ANONYMOUS)).as("匿名").isEqualTo(anonymous);
    assertThat(access.isVisibleTo(OTHER)).as("他人").isEqualTo(other);
    assertThat(access.isVisibleTo(SELF)).as("本人").isEqualTo(self);
  }
}
