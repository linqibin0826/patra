package dev.patra.identity.domain.account.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.vo.DisplayName;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.profile.Affiliation;
import dev.patra.identity.domain.account.model.vo.profile.Bio;
import dev.patra.identity.domain.account.model.vo.profile.Department;
import dev.patra.identity.domain.account.model.vo.profile.JobTitle;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.account.model.vo.profile.ProfileContent;
import dev.patra.identity.domain.account.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.account.model.vo.profile.ResearchInterest;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/// UserProfile 单元测试。不用 JUnit 的 `@DisplayName`：它和值对象同名。
class UserProfileTest {

  private static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-10-02T00:00:00Z");

  @Test
  void should_create_with_default_privacy_and_empty_lists() {
    UserProfile profile = UserProfile.create(7L);

    assertThat(profile.getId()).isNull();
    assertThat(profile.getUserId()).isEqualTo(7L);
    assertThat(profile.getDisplayName()).isEmpty();
    assertThat(profile.getBio()).isEmpty();
    assertThat(profile.getLinks()).isEmpty();
    assertThat(profile.getAffiliation()).isEmpty();
    assertThat(profile.getResearchInterests()).isEmpty();
    assertThat(profile.getPrivacy()).isEqualTo(PrivacySettings.defaults());
  }

  @Test
  void should_replace_everything_on_edit() {
    UserProfile profile = UserProfile.create(7L);
    profile.edit(fullContent());

    profile.edit(ProfileContent.builder().bio(new Bio("只剩简介")).build());

    assertThat(profile.getBio()).contains(new Bio("只剩简介"));
    assertThat(profile.getDisplayName()).isEmpty();
    assertThat(profile.getLinks()).isEmpty();
    assertThat(profile.getOccupation()).isEmpty();
    assertThat(profile.getJobTitle()).isEmpty();
    assertThat(profile.getAffiliation()).isEmpty();
    assertThat(profile.getDepartment()).isEmpty();
    assertThat(profile.getResearchInterests()).isEmpty();
  }

  @Test
  void should_hold_every_field_after_edit() {
    UserProfile profile = UserProfile.create(7L);

    profile.edit(fullContent());

    assertThat(profile.getDisplayName()).contains(new DisplayName("陈宇"));
    assertThat(profile.getLinks()).containsExactly(ProfileLink.of("实验室", "https://lab.org"));
    assertThat(profile.getOccupation()).contains(Occupation.PHYSICIAN);
    assertThat(profile.getJobTitle()).contains(JobTitle.of("主治医师"));
    assertThat(profile.getAffiliation())
        .contains(Affiliation.Registered.of(RorId.of("04jztag35"), "北京协和医院"));
    assertThat(profile.getDepartment()).contains(Department.of("心内科"));
    assertThat(profile.getResearchInterests())
        .containsExactly(interest("D006333", "Heart Failure"));
  }

  @Test
  void should_report_display_name_change() {
    UserProfile profile = UserProfile.create(7L);
    ProfileContent named = ProfileContent.builder().displayName(new DisplayName("陈宇")).build();

    assertThat(profile.edit(named)).isTrue();
    assertThat(profile.edit(named)).isFalse();
    assertThat(profile.edit(ProfileContent.builder().build())).isTrue();
  }

  @Test
  void should_reject_more_than_ten_interests() {
    List<ResearchInterest> eleven =
        IntStream.range(0, 11).mapToObj(i -> interest("D%06d".formatted(i + 1), "X")).toList();
    UserProfile profile = UserProfile.create(7L);

    assertThatThrownBy(
            () -> profile.edit(ProfileContent.builder().researchInterests(eleven).build()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_duplicate_interest_ui() {
    UserProfile profile = UserProfile.create(7L);
    List<ResearchInterest> duplicated =
        List.of(interest("D006333", "Heart Failure"), interest("D006333", "Heart Failure"));

    assertThatThrownBy(
            () -> profile.edit(ProfileContent.builder().researchInterests(duplicated).build()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_sixth_link() {
    List<ProfileLink> six =
        IntStream.range(0, 6).mapToObj(i -> ProfileLink.of(null, "https://a.org/" + i)).toList();

    assertThatThrownBy(
            () ->
                UserProfile.restore()
                    .id(1L)
                    .userId(7L)
                    .links(six)
                    .version(0L)
                    .createdAt(CREATED_AT)
                    .updatedAt(UPDATED_AT)
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_erase_content_and_reset_privacy() {
    UserProfile profile = UserProfile.create(7L);
    profile.edit(fullContent());
    profile.changePrivacy(
        PrivacySettings.of(ProfileVisibility.SELF, ProfessionalVisibility.SELF, true));
    profile.changeAvatar(Avatar.of(7L, "0123456789abcdef"));

    profile.erase();

    assertThat(profile.getUserId()).isEqualTo(7L);
    assertThat(profile.getDisplayName()).isEmpty();
    assertThat(profile.getBio()).isEmpty();
    assertThat(profile.getLinks()).isEmpty();
    assertThat(profile.getOccupation()).isEmpty();
    assertThat(profile.getJobTitle()).isEmpty();
    assertThat(profile.getAffiliation()).isEmpty();
    assertThat(profile.getDepartment()).isEmpty();
    assertThat(profile.getResearchInterests()).isEmpty();
    assertThat(profile.getPrivacy()).isEqualTo(PrivacySettings.defaults());
    assertThat(profile.getAvatar()).isEmpty();
  }

  @Test
  void should_return_previous_avatar_when_changing() {
    UserProfile profile = UserProfile.create(7L);
    Avatar first = Avatar.of(7L, "0123456789abcdef");
    Avatar second = Avatar.of(7L, "fedcba9876543210");

    assertThat(profile.changeAvatar(first)).isEmpty();
    assertThat(profile.changeAvatar(second)).contains(first);
    assertThat(profile.getAvatar()).contains(second);
  }

  @Test
  void should_reject_avatar_of_another_user() {
    UserProfile profile = UserProfile.create(7L);

    assertThatThrownBy(() -> profile.changeAvatar(Avatar.of(8L, "0123456789abcdef")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_return_removed_avatar() {
    UserProfile profile = UserProfile.create(7L);
    Avatar avatar = Avatar.of(7L, "0123456789abcdef");
    profile.changeAvatar(avatar);

    assertThat(profile.removeAvatar()).contains(avatar);
    assertThat(profile.getAvatar()).isEmpty();
    assertThat(profile.removeAvatar()).isEmpty();
  }

  @Test
  void should_change_privacy() {
    UserProfile profile = UserProfile.create(7L);
    PrivacySettings privacy =
        PrivacySettings.of(ProfileVisibility.SIGNED_IN, ProfessionalVisibility.SELF, true);

    profile.changePrivacy(privacy);

    assertThat(profile.getPrivacy()).isEqualTo(privacy);
  }

  @Test
  void should_restore_with_defaults_for_missing_privacy_and_lists() {
    UserProfile restored =
        UserProfile.restore()
            .id(1L)
            .userId(7L)
            .displayName(new DisplayName("陈宇"))
            .version(3L)
            .createdAt(CREATED_AT)
            .updatedAt(UPDATED_AT)
            .build();

    assertThat(restored.getId()).isEqualTo(1L);
    assertThat(restored.getDisplayName()).contains(new DisplayName("陈宇"));
    assertThat(restored.getVersion()).isEqualTo(3L);
    assertThat(restored.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(restored.getUpdatedAt()).isEqualTo(UPDATED_AT);
    assertThat(restored.getLinks()).isEmpty();
    assertThat(restored.getPrivacy()).isEqualTo(PrivacySettings.defaults());
  }

  /// 一份各字段都有值的资料。
  ///
  /// @return 资料内容
  private static ProfileContent fullContent() {
    return ProfileContent.builder()
        .displayName(new DisplayName("陈宇"))
        .bio(new Bio("心内科医生"))
        .links(List.of(ProfileLink.of("实验室", "https://lab.org")))
        .occupation(Occupation.PHYSICIAN)
        .jobTitle(JobTitle.of("主治医师"))
        .affiliation(Affiliation.Registered.of(RorId.of("04jztag35"), "北京协和医院"))
        .department(Department.of("心内科"))
        .researchInterests(List.of(interest("D006333", "Heart Failure")))
        .build();
  }

  /// 构造一个关注领域。
  ///
  /// @param ui MeSH UI
  /// @param name 英文名
  /// @return 关注领域
  private static ResearchInterest interest(String ui, String name) {
    return ResearchInterest.of(MeshUi.of(ui), name);
  }
}
