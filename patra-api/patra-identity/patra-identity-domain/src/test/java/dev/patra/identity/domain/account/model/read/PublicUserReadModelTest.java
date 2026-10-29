package dev.patra.identity.domain.account.model.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.profile.Affiliation;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.account.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.account.model.vo.profile.ResearchInterest;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import dev.patra.identity.domain.account.policy.AvatarPalette;
import dev.patra.identity.domain.account.policy.ProfileDisplayPolicy;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// PublicUserReadModel 单元测试：实际可见范围、对谁可见（spec §8.3 矩阵）、按请求者裁剪的视图。
class PublicUserReadModelTest {

  private static final long OWNER = 7L;
  private static final Optional<Long> ANONYMOUS = Optional.empty();
  private static final Optional<Long> OTHER = Optional.of(99L);
  private static final Optional<Long> SELF = Optional.of(OWNER);
  private static final ProfileDisplayPolicy DISPLAY =
      ProfileDisplayPolicy.of(ZoneId.of("Asia/Shanghai"));

  @Test
  void should_follow_profile_visibility_when_active_and_verified() {
    assertThat(withVisibility(ProfileVisibility.PUBLIC).audience())
        .isEqualTo(PublicAudience.PUBLIC);
    assertThat(withVisibility(ProfileVisibility.SIGNED_IN).audience())
        .isEqualTo(PublicAudience.SIGNED_IN);
    assertThat(withVisibility(ProfileVisibility.SELF).audience()).isEqualTo(PublicAudience.SELF);
  }

  @Test
  void should_be_self_only_when_unverified_or_not_active() {
    assertThat(full().emailVerified(false).build().audience()).isEqualTo(PublicAudience.SELF);
    for (UserStatus status :
        List.of(UserStatus.PENDING_DELETION, UserStatus.BANNED, UserStatus.DELETED)) {
      assertThat(full().status(status).build().audience()).isEqualTo(PublicAudience.SELF);
    }
  }

  @Test
  void should_be_visible_according_to_matrix() {
    assertVisibility(withVisibility(ProfileVisibility.PUBLIC), true, true, true);
    assertVisibility(withVisibility(ProfileVisibility.SIGNED_IN), false, true, true);
    assertVisibility(withVisibility(ProfileVisibility.SELF), false, false, true);
    assertVisibility(full().emailVerified(false).build(), false, false, true);
    assertVisibility(full().status(UserStatus.PENDING_DELETION).build(), false, false, true);
    assertVisibility(full().status(UserStatus.BANNED).build(), false, false, true);
    assertVisibility(full().status(UserStatus.DELETED).build(), false, false, false);
  }

  @Test
  void should_hide_professional_from_others_when_self_only() {
    PublicUserReadModel user = withProfessional(ProfessionalVisibility.SELF);

    assertThat(user.viewFor(OTHER, DISPLAY).professional()).isNull();
    assertThat(user.viewFor(ANONYMOUS, DISPLAY).professional()).isNull();
  }

  @Test
  void should_show_professional_to_owner_when_self_only() {
    PublicUserView view = withProfessional(ProfessionalVisibility.SELF).viewFor(SELF, DISPLAY);

    assertThat(view.professional())
        .isEqualTo(
            PublicUserView.Professional.of(
                Occupation.PHYSICIAN,
                "主治医师",
                Affiliation.Registered.of(RorId.of("04jztag35"), "北京协和医院"),
                "心内科"));
  }

  @Test
  void should_show_professional_to_everyone_when_public() {
    assertThat(full().build().viewFor(ANONYMOUS, DISPLAY).professional()).isNotNull();
  }

  @Test
  void should_hide_links_when_email_unverified_even_for_owner() {
    PublicUserReadModel unverified = full().emailVerified(false).build();

    assertThat(unverified.viewFor(SELF, DISPLAY).links()).isEmpty();
    assertThat(full().build().viewFor(ANONYMOUS, DISPLAY).links()).hasSize(1);
  }

  @Test
  void should_be_indexable_only_when_public_and_opted_in() {
    assertThat(withPrivacy(ProfileVisibility.PUBLIC, true).viewFor(ANONYMOUS, DISPLAY).indexable())
        .isTrue();
    assertThat(withPrivacy(ProfileVisibility.PUBLIC, false).viewFor(ANONYMOUS, DISPLAY).indexable())
        .isFalse();
    assertThat(withPrivacy(ProfileVisibility.SIGNED_IN, true).viewFor(OTHER, DISPLAY).indexable())
        .isFalse();
    PublicUserReadModel unverified =
        full()
            .emailVerified(false)
            .privacy(
                PrivacySettings.of(ProfileVisibility.PUBLIC, ProfessionalVisibility.PUBLIC, true))
            .build();
    assertThat(unverified.viewFor(SELF, DISPLAY).indexable()).isFalse();
  }

  @Test
  void should_drop_professional_when_all_four_empty() {
    PublicUserReadModel user =
        full().occupation(null).jobTitle(null).affiliation(null).department(null).build();

    assertThat(user.viewFor(SELF, DISPLAY).professional()).isNull();
  }

  @Test
  void should_carry_header_fields_and_interests() {
    PublicUserView view = full().build().viewFor(ANONYMOUS, DISPLAY);

    assertThat(view.username()).isEqualTo("chen-yu");
    assertThat(view.displayName()).isEqualTo("陈宇");
    assertThat(view.audience()).isEqualTo(PublicAudience.PUBLIC);
    assertThat(view.bio()).isEqualTo("心内科医生");
    assertThat(view.joinedMonth()).isEqualTo(YearMonth.of(2026, 10));
    assertThat(view.researchInterests())
        .containsExactly(ResearchInterest.of(MeshUi.of("D006333"), "Heart Failure"));
  }

  @Test
  void should_carry_avatar_and_palette_for_any_requester() {
    Avatar avatar = Avatar.of(OWNER, "0123456789abcdef");
    PublicUserReadModel user = full().avatar(avatar).build();

    for (Optional<Long> requester : List.of(ANONYMOUS, SELF)) {
      PublicUserView view = user.viewFor(requester, DISPLAY);
      assertThat(view.avatar()).isEqualTo(avatar);
      assertThat(view.avatarPalette()).isEqualTo(AvatarPalette.of(OWNER));
    }
  }

  @Test
  void should_carry_user_id_in_view() {
    assertThat(full().build().viewFor(ANONYMOUS, DISPLAY).userId()).isEqualTo(OWNER);
  }

  @Test
  void should_throw_when_not_visible() {
    PublicUserReadModel user = withVisibility(ProfileVisibility.SELF);

    assertThatThrownBy(() -> user.viewFor(OTHER, DISPLAY))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_compute_joined_month_in_display_zone() {
    assertThat(DISPLAY.joinedMonth(Instant.parse("2026-10-31T16:30:00Z")))
        .isEqualTo(YearMonth.of(2026, 11));
  }

  /// 断言匿名、别人、本人三列的可见性。
  ///
  /// @param user 读模型
  /// @param anonymous 匿名是否可见
  /// @param other 别人是否可见
  /// @param self 本人是否可见
  private static void assertVisibility(
      PublicUserReadModel user, boolean anonymous, boolean other, boolean self) {
    assertThat(user.isVisibleTo(ANONYMOUS)).isEqualTo(anonymous);
    assertThat(user.isVisibleTo(OTHER)).isEqualTo(other);
    assertThat(user.isVisibleTo(SELF)).isEqualTo(self);
  }

  /// 主页可见性为给定值、其余同 `full()`。
  ///
  /// @param visibility 主页可见性
  /// @return 读模型
  private static PublicUserReadModel withVisibility(ProfileVisibility visibility) {
    return withPrivacy(visibility, false);
  }

  /// 主页可见性与收录开关为给定值、其余同 `full()`。
  ///
  /// @param visibility 主页可见性
  /// @param indexing 是否允许收录
  /// @return 读模型
  private static PublicUserReadModel withPrivacy(ProfileVisibility visibility, boolean indexing) {
    return full()
        .privacy(PrivacySettings.of(visibility, ProfessionalVisibility.PUBLIC, indexing))
        .build();
  }

  /// 专业背景可见性为给定值、其余同 `full()`。
  ///
  /// @param professional 专业背景可见性
  /// @return 读模型
  private static PublicUserReadModel withProfessional(ProfessionalVisibility professional) {
    return full()
        .privacy(PrivacySettings.of(ProfileVisibility.PUBLIC, professional, false))
        .build();
  }

  /// 正常、已验证、各字段都有值、隐私默认的读模型。
  ///
  /// @return 构建器
  private static PublicUserReadModel.PublicUserReadModelBuilder full() {
    return PublicUserReadModel.builder()
        .userId(OWNER)
        .username("chen-yu")
        .displayName("陈宇")
        .status(UserStatus.ACTIVE)
        .emailVerified(true)
        .joinedAt(Instant.parse("2026-10-05T02:00:00Z"))
        .bio("心内科医生")
        .links(List.of(ProfileLink.of("实验室", "https://lab.org")))
        .occupation(Occupation.PHYSICIAN)
        .jobTitle("主治医师")
        .affiliation(Affiliation.Registered.of(RorId.of("04jztag35"), "北京协和医院"))
        .department("心内科")
        .researchInterests(List.of(ResearchInterest.of(MeshUi.of("D006333"), "Heart Failure")))
        .privacy(PrivacySettings.defaults());
  }
}
