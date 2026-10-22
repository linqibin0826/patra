package dev.patra.identity.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.aggregate.UserProfile;
import dev.patra.identity.domain.model.enums.Occupation;
import dev.patra.identity.domain.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.model.enums.ProfileVisibility;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.read.PublicUserReadModel;
import dev.patra.identity.domain.model.read.UserAccountReadModel;
import dev.patra.identity.domain.model.vo.DisplayName;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.Username;
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
import dev.patra.identity.infra.adapter.persistence.UserProfileRepositoryAdapter;
import dev.patra.identity.infra.adapter.persistence.UserRepositoryAdapter;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.infra.config.UsernameChangePolicyITSpringConfig;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserReadAdapter 集成测试：账号读模型要拼上资料，昵称为空时回退成用户名。
/// 不用 JUnit 的 `@DisplayName`：它和值对象同名。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  UserRepositoryAdapter.class,
  UserProfileRepositoryAdapter.class,
  UserReadAdapter.class,
  UsernameChangePolicyITSpringConfig.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackages = "dev.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
class UserReadAdapterIT {

  private static final Instant T0 = Instant.parse("2026-10-20T08:00:00Z");

  @Autowired private UserRepositoryAdapter users;
  @Autowired private UserProfileRepositoryAdapter profiles;
  @Autowired private UserReadAdapter reader;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  @Test
  void should_fall_back_to_username_when_display_name_is_empty() {
    User saved =
        users.save(User.register(EmailAddress.of("read.me@example.com"), Username.of("u-read-me")));
    profiles.save(UserProfile.create(saved.getId()));

    assertThat(reader.findAccount(saved.getId()))
        .contains(
            UserAccountReadModel.builder()
                .userId(saved.getId())
                .email("read.me@example.com")
                .emailVerified(false)
                .username("u-read-me")
                .displayName("u-read-me")
                .status(UserStatus.ACTIVE)
                .build());
    assertThat(reader.findAccount(1L)).isEmpty();
  }

  @Test
  void should_use_display_name_and_compute_next_username_change() {
    User saved =
        users.save(User.register(EmailAddress.of("named@example.com"), Username.of("u-named")));
    UserProfile profile = profiles.save(UserProfile.create(saved.getId()));
    profile.edit(ProfileContent.builder().displayName(new DisplayName("陈宇")).build());
    profiles.save(profile);
    saved.markEmailVerified(T0);
    saved.ban(T0);
    users.save(saved);
    jdbcTemplate.update(
        "UPDATE idn_user SET username_changed_at = ? WHERE id = ?",
        Timestamp.from(T0),
        saved.getId());
    // 绕过 JPA 改的列不会进一级缓存，清掉再读才是库里的值
    entityManager.clear();

    UserAccountReadModel account = reader.findAccount(saved.getId()).orElseThrow();

    assertThat(account.displayName()).isEqualTo("陈宇");
    assertThat(account.emailVerified()).isTrue();
    assertThat(account.status()).isEqualTo(UserStatus.BANNED);
    assertThat(account.nextUsernameChangeAt()).isEqualTo(T0.plus(Duration.ofDays(30)));
    assertThat(account.deletionDueAt()).isNull();
  }

  @Test
  void should_fail_loudly_when_profile_missing() {
    User saved =
        users.save(
            User.register(EmailAddress.of("no-profile@example.com"), Username.of("u-no-profile")));

    assertThatThrownBy(() -> reader.findAccount(saved.getId()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_read_public_identity_by_username_and_by_id() {
    User saved =
        users.save(User.register(EmailAddress.of("pub@example.com"), Username.of("u-pub")));
    profiles.save(UserProfile.create(saved.getId()));
    PublicUserReadModel expected =
        PublicUserReadModel.builder()
            .userId(saved.getId())
            .username("u-pub")
            .displayName("u-pub")
            .status(UserStatus.ACTIVE)
            .emailVerified(false)
            .build();

    assertThat(reader.findPublicIdentity(Username.of("u-pub")))
        .get()
        .usingRecursiveComparison()
        .ignoringFields("joinedAt")
        .isEqualTo(expected);
    assertThat(reader.findPublicIdentityByUserId(saved.getId()))
        .get()
        .usingRecursiveComparison()
        .ignoringFields("joinedAt")
        .isEqualTo(expected);
    assertThat(reader.findPublicIdentity(Username.of("nobody"))).isEmpty();
    assertThat(reader.findPublicIdentityByUserId(1L)).isEmpty();
  }

  @Test
  void should_read_public_page_fields() {
    User saved =
        users.save(User.register(EmailAddress.of("page@example.com"), Username.of("u-page")));
    UserProfile profile = profiles.save(UserProfile.create(saved.getId()));
    profile.edit(
        ProfileContent.builder()
            .displayName(new DisplayName("陈宇"))
            .bio(new Bio("心内科医生"))
            .links(List.of(ProfileLink.of("实验室", "https://lab.org")))
            .occupation(Occupation.PHYSICIAN)
            .jobTitle(JobTitle.of("主治医师"))
            .affiliation(Affiliation.Registered.of(RorId.of("04jztag35"), "北京协和医院"))
            .department(Department.of("心内科"))
            .researchInterests(
                List.of(
                    ResearchInterest.of(MeshUi.of("D006333"), "Heart Failure"),
                    ResearchInterest.of(MeshUi.of("D001281"), "Atrial Fibrillation")))
            .build());
    PrivacySettings privacy =
        PrivacySettings.of(ProfileVisibility.SIGNED_IN, ProfessionalVisibility.SELF, true);
    profile.changePrivacy(privacy);
    profiles.save(profile);

    PublicUserReadModel page = reader.findPublicIdentity(Username.of("u-page")).orElseThrow();

    assertThat(page.displayName()).isEqualTo("陈宇");
    assertThat(page.bio()).isEqualTo("心内科医生");
    assertThat(page.links()).containsExactly(ProfileLink.of("实验室", "https://lab.org"));
    assertThat(page.occupation()).isEqualTo(Occupation.PHYSICIAN);
    assertThat(page.jobTitle()).isEqualTo("主治医师");
    assertThat(page.affiliation())
        .isEqualTo(Affiliation.Registered.of(RorId.of("04jztag35"), "北京协和医院"));
    assertThat(page.department()).isEqualTo("心内科");
    assertThat(page.researchInterests())
        .extracting(interest -> interest.ui().value())
        .containsExactly("D006333", "D001281");
    assertThat(page.privacy()).isEqualTo(privacy);
  }

  @Test
  void should_use_user_created_at_as_joined_at() {
    User saved =
        users.save(User.register(EmailAddress.of("joined@example.com"), Username.of("u-joined")));
    profiles.save(UserProfile.create(saved.getId()));
    jdbcTemplate.update(
        "UPDATE idn_user SET created_at = ? WHERE id = ?", Timestamp.from(T0), saved.getId());
    entityManager.clear();

    assertThat(reader.findPublicIdentityByUserId(saved.getId()).orElseThrow().joinedAt())
        .isEqualTo(T0);
  }
}
