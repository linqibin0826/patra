package dev.patra.identity.domain.account.model.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.policy.AvatarPalette;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// UserSummaryReadModel 单元测试：按状态与访客出摘要（PAP-108 spec 5.3 表格）。
class UserSummaryReadModelTest {

  private static final long OWNER = 7L;
  private static final Avatar AVATAR = Avatar.of(OWNER, "0123456789abcdef");

  @Test
  void should_fall_back_to_username_when_display_name_is_null() {
    UserSummaryView view = active().displayName(null).build().viewFor(Optional.empty());

    assertThat(view.displayName()).isEqualTo("chen-yu");
  }

  @Test
  void should_carry_identity_fields_for_active_user() {
    UserSummaryView view = active().build().viewFor(Optional.empty());

    assertThat(view.userId()).isEqualTo(OWNER);
    assertThat(view.username()).isEqualTo("chen-yu");
    assertThat(view.displayName()).isEqualTo("陈宇");
    assertThat(view.avatar()).isEqualTo(AVATAR);
    assertThat(view.avatarPalette()).isEqualTo(AvatarPalette.of(OWNER));
    assertThat(view.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(view.emailVerified()).isTrue();
    assertThat(view.profileVisible()).isTrue();
  }

  @Test
  void should_drop_avatar_for_banned_user() {
    UserSummaryView view = active().status(UserStatus.BANNED).build().viewFor(Optional.empty());

    assertThat(view.avatar()).isNull();
    assertThat(view.username()).isEqualTo("chen-yu");
    assertThat(view.profileVisible()).isFalse();
  }

  @Test
  void should_return_placeholder_for_deleted_user() {
    UserSummaryReadModel deleted = active().status(UserStatus.DELETED).displayName(null).build();

    for (Optional<Long> viewer : List.of(Optional.<Long>empty(), Optional.of(OWNER))) {
      UserSummaryView view = deleted.viewFor(viewer);

      assertThat(view.username()).isNull();
      assertThat(view.displayName()).isNull();
      assertThat(view.avatar()).isNull();
      assertThat(view.emailVerified()).isFalse();
      assertThat(view.profileVisible()).isFalse();
      assertThat(view.status()).isEqualTo(UserStatus.DELETED);
      assertThat(view.avatarPalette()).isEqualTo(AvatarPalette.of(OWNER));
    }
  }

  @Test
  void should_compute_profile_visible_for_viewer() {
    UserSummaryReadModel user = active().profileVisibility(ProfileVisibility.SIGNED_IN).build();

    assertThat(user.viewFor(Optional.empty()).profileVisible()).isFalse();
    assertThat(user.viewFor(Optional.of(99L)).profileVisible()).isTrue();
    assertThat(user.viewFor(Optional.of(OWNER)).profileVisible()).isTrue();
  }

  /// 正常、邮箱已验证、主页公开、设了昵称和头像。
  ///
  /// @return 构造器
  private static UserSummaryReadModel.UserSummaryReadModelBuilder active() {
    return UserSummaryReadModel.builder()
        .userId(OWNER)
        .username("chen-yu")
        .displayName("陈宇")
        .avatar(AVATAR)
        .status(UserStatus.ACTIVE)
        .emailVerified(true)
        .profileVisibility(ProfileVisibility.PUBLIC);
  }
}
