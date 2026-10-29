package dev.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.aggregate.UserProfile;
import dev.patra.identity.domain.model.enums.Occupation;
import dev.patra.identity.domain.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.model.enums.ProfileVisibility;
import dev.patra.identity.domain.model.vo.DisplayName;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.model.vo.avatar.Avatar;
import dev.patra.identity.domain.model.vo.profile.Affiliation;
import dev.patra.identity.domain.model.vo.profile.Bio;
import dev.patra.identity.domain.model.vo.profile.Department;
import dev.patra.identity.domain.model.vo.profile.JobTitle;
import dev.patra.identity.domain.model.vo.profile.MeshUi;
import dev.patra.identity.domain.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.model.vo.profile.ProfileContent;
import dev.patra.identity.domain.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.model.vo.profile.ResearchInterest;
import dev.patra.identity.domain.model.vo.profile.RorId;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// UserProfileRepositoryAdapter 集成测试。不用 JUnit 的 `@DisplayName`：它和值对象同名。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  UserRepositoryAdapter.class,
  UserProfileRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackages = "dev.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
class UserProfileRepositoryAdapterIT {

  @Autowired private UserRepositoryAdapter users;
  @Autowired private UserProfileRepositoryAdapter profiles;
  @Autowired private JdbcTemplate jdbcTemplate;

  /// 资料保存在事务外调用时被拒绝（`MANDATORY`）。
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void should_reject_save_outside_transaction() {
    assertThatThrownBy(() -> profiles.save(UserProfile.create(1L)))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void should_save_empty_profile_and_find_by_user_id() {
    User user =
        users.save(User.register(EmailAddress.of("profile@example.com"), Username.of("u-profile")));

    UserProfile saved = profiles.save(UserProfile.create(user.getId()));

    assertThat(saved.getId()).isPositive();
    assertThat(saved.getVersion()).isZero();
    assertThat(profiles.findByUserId(user.getId()))
        .get()
        .extracting(UserProfile::getDisplayName)
        .isEqualTo(Optional.empty());
    assertThat(profiles.findByUserId(-1L)).isEmpty();
  }

  @Test
  void should_persist_rename() {
    User user =
        users.save(User.register(EmailAddress.of("rename@example.com"), Username.of("u-rename")));
    UserProfile saved = profiles.save(UserProfile.create(user.getId()));

    saved.edit(ProfileContent.builder().displayName(new DisplayName("陈宇")).build());
    profiles.save(saved);

    assertThat(profiles.findByUserId(user.getId()))
        .get()
        .extracting(profile -> profile.getDisplayName().map(DisplayName::value))
        .isEqualTo(Optional.of("陈宇"));
  }

  @Test
  void should_reject_second_profile_for_same_user() {
    User user =
        users.save(
            User.register(
                EmailAddress.of("twice-profile@example.com"), Username.of("u-twice-profile")));
    profiles.save(UserProfile.create(user.getId()));

    assertThatThrownBy(() -> profiles.save(UserProfile.create(user.getId())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void should_round_trip_every_field() {
    long userId = newUser("full");
    UserProfile profile = profiles.save(UserProfile.create(userId));
    profile.edit(fullContent());
    profile.changePrivacy(
        PrivacySettings.of(ProfileVisibility.SIGNED_IN, ProfessionalVisibility.SELF, true));

    profiles.save(profile);

    UserProfile read = profiles.findByUserId(userId).orElseThrow();
    assertThat(read.getDisplayName()).contains(new DisplayName("陈宇"));
    assertThat(read.getBio()).contains(new Bio("心内科医生\n业余写科普"));
    assertThat(read.getLinks())
        .containsExactly(
            ProfileLink.of("实验室", "https://lab.org"), ProfileLink.of(null, "https://b.org"));
    assertThat(read.getOccupation()).contains(Occupation.PHYSICIAN);
    assertThat(read.getJobTitle()).contains(JobTitle.of("主治医师"));
    assertThat(read.getAffiliation())
        .contains(Affiliation.Registered.of(RorId.of("04jztag35"), "北京协和医院"));
    assertThat(read.getDepartment()).contains(Department.of("心内科"));
    assertThat(read.getResearchInterests())
        .containsExactly(
            interest("D006333", "Heart Failure"),
            interest("D001281", "AF"),
            interest("D003920", "Diabetes Mellitus"));
    assertThat(read.getPrivacy())
        .isEqualTo(
            PrivacySettings.of(ProfileVisibility.SIGNED_IN, ProfessionalVisibility.SELF, true));
  }

  @Test
  void should_round_trip_free_text_affiliation() {
    long userId = newUser("free");
    UserProfile profile = profiles.save(UserProfile.create(userId));
    profile.edit(ProfileContent.builder().affiliation(Affiliation.FreeText.of("某县人民医院")).build());

    profiles.save(profile);

    assertThat(profiles.findByUserId(userId).orElseThrow().getAffiliation())
        .contains(Affiliation.FreeText.of("某县人民医院"));
  }

  @Test
  void should_reorder_children_without_unique_violation() {
    long userId = newUser("reorder");
    UserProfile profile = profiles.save(UserProfile.create(userId));
    ProfileLink a = ProfileLink.of(null, "https://a.org");
    ProfileLink b = ProfileLink.of(null, "https://b.org");
    ResearchInterest x = interest("D000001", "X");
    ResearchInterest y = interest("D000002", "Y");
    ResearchInterest z = interest("D000003", "Z");

    profile = save(profile, List.of(a, b), List.of(x, y, z));
    profile = save(profile, List.of(b, a), List.of(z));
    profile = save(profile, List.of(b, a), List.of(y, x));

    UserProfile read = profiles.findByUserId(userId).orElseThrow();
    assertThat(read.getLinks()).containsExactly(b, a);
    assertThat(read.getResearchInterests()).containsExactly(y, x);
  }

  @Test
  void should_keep_children_of_other_users() {
    long first = newUser("first");
    long second = newUser("second");
    UserProfile firstProfile = profiles.save(UserProfile.create(first));
    UserProfile secondProfile = profiles.save(UserProfile.create(second));
    save(firstProfile, List.of(ProfileLink.of(null, "https://a.org")), List.of());
    save(secondProfile, List.of(ProfileLink.of(null, "https://a.org")), List.of());

    save(profiles.findByUserId(second).orElseThrow(), List.of(), List.of());

    assertThat(profiles.findByUserId(first).orElseThrow().getLinks()).hasSize(1);
    assertThat(profiles.findByUserId(second).orElseThrow().getLinks()).isEmpty();
  }

  @Test
  void should_read_default_privacy_from_new_profile() {
    long userId = newUser("defaults");

    profiles.save(UserProfile.create(userId));

    assertThat(profiles.findByUserId(userId).orElseThrow().getPrivacy())
        .isEqualTo(PrivacySettings.defaults());
  }

  /// 注册一个用户。
  ///
  /// @param name 用户名后缀
  /// @return 用户 ID
  private long newUser(String name) {
    return users
        .save(User.register(EmailAddress.of(name + "@example.com"), Username.of("u-" + name)))
        .getId();
  }

  /// 只换链接与关注领域后保存，返回保存后的资料。
  ///
  /// @param profile 资料
  /// @param links 链接
  /// @param interests 关注领域
  /// @return 保存后的资料
  private UserProfile save(
      UserProfile profile, List<ProfileLink> links, List<ResearchInterest> interests) {
    profile.edit(ProfileContent.builder().links(links).researchInterests(interests).build());
    return profiles.save(profile);
  }

  /// 一份各字段都有值的资料。
  ///
  /// @return 资料内容
  private static ProfileContent fullContent() {
    return ProfileContent.builder()
        .displayName(new DisplayName("陈宇"))
        .bio(new Bio("心内科医生\n业余写科普"))
        .links(
            List.of(
                ProfileLink.of("实验室", "https://lab.org"), ProfileLink.of(null, "https://b.org")))
        .occupation(Occupation.PHYSICIAN)
        .jobTitle(JobTitle.of("主治医师"))
        .affiliation(Affiliation.Registered.of(RorId.of("04jztag35"), "北京协和医院"))
        .department(Department.of("心内科"))
        .researchInterests(
            List.of(
                interest("D006333", "Heart Failure"),
                interest("D001281", "AF"),
                interest("D003920", "Diabetes Mellitus")))
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

  @Test
  void should_round_trip_avatar_object_key() {
    User user =
        users.save(User.register(EmailAddress.of("avatar@example.com"), Username.of("u-avatar")));
    UserProfile profile = profiles.save(UserProfile.create(user.getId()));
    Avatar avatar = Avatar.of(user.getId(), "0123456789abcdef");
    profile.changeAvatar(avatar);

    profiles.save(profile);

    assertThat(profiles.findByUserId(user.getId()).orElseThrow().getAvatar()).contains(avatar);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT avatar_object_key FROM idn_user_profile WHERE user_id = ?",
                String.class,
                user.getId()))
        .isEqualTo("avatar/" + user.getId() + "/0123456789abcdef-512.webp");
  }

  /// 每个用例一个坏键：违反约束会让测试事务中止，同一事务里不能接着发语句。
  @ParameterizedTest
  @ValueSource(
      strings = {
        "avatar/999/0123456789abcdef-512.webp",
        "avatar/%d/XYZ-512.webp",
        "avatar/%d/0123456789abcdef-128.webp"
      })
  void should_reject_malformed_or_foreign_avatar_key_at_database(String pattern) {
    User user =
        users.save(
            User.register(EmailAddress.of("avatar.bad@example.com"), Username.of("u-avatar-bad")));
    profiles.save(UserProfile.create(user.getId()));
    String key = pattern.formatted(user.getId());

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE idn_user_profile SET avatar_object_key = ? WHERE user_id = ?",
                    key,
                    user.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
