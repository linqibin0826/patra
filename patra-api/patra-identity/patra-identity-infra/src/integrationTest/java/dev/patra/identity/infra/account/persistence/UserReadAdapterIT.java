package dev.patra.identity.infra.account.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.AvatarOwner;
import dev.patra.identity.domain.account.model.read.PublicUserReadModel;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.read.UserSummaryReadModel;
import dev.patra.identity.domain.account.model.vo.DisplayName;
import dev.patra.identity.domain.account.model.vo.DisplayNamePrefix;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.UsernamePrefix;
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
import dev.patra.identity.infra.account.persistence.mapper.UserJpaMapper;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.infra.config.UsernameChangePolicyITSpringConfig;
import dev.patra.identity.infra.session.persistence.mapper.UserLoginRecordJpaMapper;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
@ComponentScan(basePackageClasses = {UserJpaMapper.class, UserLoginRecordJpaMapper.class})
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

  @Test
  void should_carry_avatar_in_account_and_public_identity() {
    User saved =
        users.save(User.register(EmailAddress.of("ava@example.com"), Username.of("u-ava")));
    UserProfile profile = profiles.save(UserProfile.create(saved.getId()));
    Avatar avatar = Avatar.of(saved.getId(), "0123456789abcdef");
    profile.changeAvatar(avatar);
    profiles.save(profile);

    assertThat(reader.findAccount(saved.getId()).orElseThrow().avatar()).isEqualTo(avatar);
    assertThat(reader.findPublicIdentity(Username.of("u-ava")).orElseThrow().avatar())
        .isEqualTo(avatar);
  }

  @Test
  void should_find_avatar_owner_by_user_id() {
    User saved =
        users.save(User.register(EmailAddress.of("owner@example.com"), Username.of("u-owner")));
    UserProfile profile = profiles.save(UserProfile.create(saved.getId()));
    Avatar avatar = Avatar.of(saved.getId(), "0123456789abcdef");
    profile.changeAvatar(avatar);
    profiles.save(profile);
    User bare =
        users.save(User.register(EmailAddress.of("bare@example.com"), Username.of("u-bare")));
    profiles.save(UserProfile.create(bare.getId()));

    assertThat(reader.findAvatarOwner(saved.getId()))
        .contains(AvatarOwner.of(saved.getId(), UserStatus.ACTIVE, avatar));
    assertThat(reader.findAvatarOwner(bare.getId()))
        .contains(AvatarOwner.of(bare.getId(), UserStatus.ACTIVE, null));
    assertThat(reader.findAvatarOwner(Long.MAX_VALUE)).isEmpty();

    jdbcTemplate.update("UPDATE idn_user SET username = 'u-owner-new' WHERE id = ?", saved.getId());
    entityManager.clear();

    assertThat(reader.findAvatarOwner(saved.getId()))
        .contains(AvatarOwner.of(saved.getId(), UserStatus.ACTIVE, avatar));
  }

  @Test
  void should_read_summaries_in_batch() {
    User named =
        users.save(User.register(EmailAddress.of("sum.a@example.com"), Username.of("u-sum-a")));
    UserProfile namedProfile = profiles.save(UserProfile.create(named.getId()));
    namedProfile.edit(ProfileContent.builder().displayName(new DisplayName("陈宇")).build());
    Avatar avatar = Avatar.of(named.getId(), "0123456789abcdef");
    namedProfile.changeAvatar(avatar);
    namedProfile.changePrivacy(
        PrivacySettings.of(ProfileVisibility.SIGNED_IN, ProfessionalVisibility.PUBLIC, false));
    profiles.save(namedProfile);
    User bare =
        users.save(User.register(EmailAddress.of("sum.b@example.com"), Username.of("u-sum-b")));
    profiles.save(UserProfile.create(bare.getId()));
    User gone =
        users.save(User.register(EmailAddress.of("sum.c@example.com"), Username.of("u-sum-c")));
    profiles.save(UserProfile.create(gone.getId()));
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'DELETED', deleted_at = now(), email = NULL WHERE id = ?",
        gone.getId());
    entityManager.clear();

    Map<Long, UserSummaryReadModel> byId =
        reader
            .findSummaries(List.of(named.getId(), bare.getId(), gone.getId(), Long.MAX_VALUE))
            .stream()
            .collect(Collectors.toMap(UserSummaryReadModel::userId, Function.identity()));

    assertThat(byId).containsOnlyKeys(named.getId(), bare.getId(), gone.getId());
    UserSummaryReadModel a = byId.get(named.getId());
    assertThat(a.username()).isEqualTo("u-sum-a");
    assertThat(a.displayName()).isEqualTo("陈宇");
    assertThat(a.avatar()).isEqualTo(avatar);
    assertThat(a.profileVisibility()).isEqualTo(ProfileVisibility.SIGNED_IN);
    assertThat(a.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(a.emailVerified()).isFalse();
    UserSummaryReadModel b = byId.get(bare.getId());
    assertThat(b.displayName()).isNull();
    assertThat(b.avatar()).isNull();
    assertThat(b.profileVisibility()).isEqualTo(ProfileVisibility.PUBLIC);
    assertThat(byId.get(gone.getId()).status()).isEqualTo(UserStatus.DELETED);
  }

  @Test
  void should_return_empty_summaries_for_empty_input() {
    assertThat(reader.findSummaries(List.of())).isEmpty();
  }

  @Test
  void should_find_active_summaries_by_username_prefix_in_byte_order() {
    for (String name : List.of("chena", "chen_li", "chen9", "chen-yu", "chen", "chex")) {
      saveWithProfile(name);
    }
    saveWithStatus("chen-banned", "BANNED");
    saveWithStatus("chen-wait", "PENDING_DELETION");
    saveWithStatus("chen-gone", "DELETED");

    assertThat(reader.findActiveSummariesByUsernamePrefix(prefix("chen"), 10))
        .extracting(UserSummaryReadModel::username)
        .containsExactly("chen", "chen-yu", "chen9", "chen_li", "chena");
  }

  @Test
  void should_not_treat_separators_as_wildcards() {
    for (String name : List.of("chen-yu", "chen_li", "chena", "chen0")) {
      saveWithProfile(name);
    }

    assertThat(reader.findActiveSummariesByUsernamePrefix(prefix("chen_"), 10))
        .extracting(UserSummaryReadModel::username)
        .containsExactly("chen_li");
    assertThat(reader.findActiveSummariesByUsernamePrefix(prefix("chen-"), 10))
        .extracting(UserSummaryReadModel::username)
        .containsExactly("chen-yu");
  }

  @Test
  void should_cut_prefix_candidates_to_limit() {
    for (String name : List.of("chen9", "chen", "chen-yu")) {
      saveWithProfile(name);
    }

    assertThat(reader.findActiveSummariesByUsernamePrefix(prefix("chen"), 2))
        .extracting(UserSummaryReadModel::username)
        .containsExactly("chen", "chen-yu");
  }

  @Test
  void should_match_single_character_username_prefix() {
    for (String name : List.of("cat", "c-ow", "czar", "dog", "bat")) {
      saveWithProfile(name);
    }

    assertThat(reader.findActiveSummariesByUsernamePrefix(prefix("c"), 10))
        .extracting(UserSummaryReadModel::username)
        .containsExactly("c-ow", "cat", "czar");
  }

  @Test
  void should_index_active_usernames_in_byte_order() {
    String definition =
        jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_idn_user_username_prefix'",
            String.class);

    assertThat(definition).contains("(username COLLATE \"C\")").contains("'ACTIVE'");
  }

  @Test
  void should_index_display_names_in_byte_order() {
    String definition =
        jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes"
                + " WHERE indexname = 'idx_idn_user_profile_display_name_prefix'",
            String.class);

    assertThat(definition)
        .contains("lower((display_name)::text)")
        .contains("COLLATE \"C\"")
        .contains("display_name IS NOT NULL");
  }

  @Test
  void should_read_summaries_by_usernames() {
    long active = saveWithProfile("by-name-a");
    long banned = saveWithStatus("by-name-b", "BANNED");

    Map<String, UserSummaryReadModel> found =
        reader
            .findSummariesByUsernames(
                List.of(
                    Username.of("by-name-a"), Username.of("by-name-b"), Username.of("by-name-x")))
            .stream()
            .collect(Collectors.toMap(UserSummaryReadModel::username, Function.identity()));

    assertThat(found).containsOnlyKeys("by-name-a", "by-name-b");
    assertThat(found.get("by-name-a").userId()).isEqualTo(active);
    assertThat(found.get("by-name-b").userId()).isEqualTo(banned);
    assertThat(found.get("by-name-b").status()).isEqualTo(UserStatus.BANNED);
  }

  @Test
  void should_return_empty_summaries_for_no_usernames() {
    assertThat(reader.findSummariesByUsernames(List.of())).isEmpty();
  }

  @Test
  void should_find_active_summaries_by_display_name_prefix_in_code_point_order() {
    saveWithDisplayName("u-zw", "张伟");
    saveWithDisplayName("u-zsf", "张三丰");
    saveWithDisplayName("u-zs", "张三");
    saveWithDisplayName("u-zz", "章子");
    applyStatus(saveWithDisplayName("u-zf", "张飞"), "BANNED");
    saveWithProfile("u-zn");

    assertThat(reader.findActiveSummariesByDisplayNamePrefix(nickname("张"), 10))
        .extracting(UserSummaryReadModel::displayName)
        .containsExactly("张三", "张三丰", "张伟");
  }

  @Test
  void should_match_display_names_case_insensitively() {
    saveWithDisplayName("u-cy", "Chen Yu");
    saveWithDisplayName("u-cl", "CHEN Li");
    saveWithDisplayName("u-cg", "Cheng");
    saveWithDisplayName("u-cx", "Chex");

    assertThat(reader.findActiveSummariesByDisplayNamePrefix(nickname("Chen"), 10))
        .extracting(UserSummaryReadModel::displayName)
        .containsExactly("CHEN Li", "Chen Yu", "Cheng");
  }

  @Test
  void should_break_display_name_ties_by_username() {
    // 倒着存：同名的人不靠存入顺序或物理位置碰巧排对
    for (String name : List.of("u-tie-e", "u-tie-d", "u-tie-c", "u-tie-b", "u-tie-a")) {
      saveWithDisplayName(name, "张三");
    }

    assertThat(reader.findActiveSummariesByDisplayNamePrefix(nickname("张三"), 10))
        .extracting(UserSummaryReadModel::username)
        .containsExactly("u-tie-a", "u-tie-b", "u-tie-c", "u-tie-d", "u-tie-e");
  }

  @Test
  void should_bound_supplementary_character_prefix_by_next_code_point() {
    // U+1F3FF 的低位代理是 \uDFFF：按 char 加一会拆坏代理对，按码点加一是 U+1F400
    saveWithDisplayName("u-dark", "🏿Dark");
    saveWithDisplayName("u-rat", "🐀Rat");

    assertThat(reader.findActiveSummariesByDisplayNamePrefix(nickname("🏿"), 10))
        .extracting(UserSummaryReadModel::username)
        .containsExactly("u-dark");
  }

  @Test
  void should_cut_display_name_candidates_to_limit() {
    saveWithDisplayName("u-zw", "张伟");
    saveWithDisplayName("u-zs", "张三");
    saveWithDisplayName("u-zsf", "张三丰");

    assertThat(reader.findActiveSummariesByDisplayNamePrefix(nickname("张"), 2))
        .extracting(UserSummaryReadModel::displayName)
        .containsExactly("张三", "张三丰");
  }

  /// 存一个正常账号和它的资料行。
  ///
  /// @param username 用户名，邮箱按它拼
  /// @return 用户 ID
  private long saveWithProfile(String username) {
    User saved =
        users.save(
            User.register(EmailAddress.of(username + "@example.com"), Username.of(username)));
    profiles.save(UserProfile.create(saved.getId()));
    return saved.getId();
  }

  /// 存一个正常账号，资料里设好昵称。
  ///
  /// @param username 用户名
  /// @param displayName 昵称，已规范化
  /// @return 用户 ID
  private long saveWithDisplayName(String username, String displayName) {
    long id = saveWithProfile(username);
    UserProfile profile = profiles.findByUserId(id).orElseThrow();
    profile.edit(ProfileContent.builder().displayName(new DisplayName(displayName)).build());
    profiles.save(profile);
    return id;
  }

  /// 存一个账号，再用 SQL 改到给定状态。
  ///
  /// @param username 用户名
  /// @param status `BANNED`、`PENDING_DELETION` 或 `DELETED`
  /// @return 用户 ID
  private long saveWithStatus(String username, String status) {
    return applyStatus(saveWithProfile(username), status);
  }

  /// 用 SQL 把已存的账号改到给定状态（与 `UserSummariesFlowIT` 的改法相同）。
  ///
  /// @param id 用户 ID
  /// @param status `BANNED`、`PENDING_DELETION` 或 `DELETED`
  /// @return 用户 ID
  private long applyStatus(long id, String status) {
    String assignments =
        switch (status) {
          case "BANNED" -> "status = 'BANNED', banned_at = now()";
          case "PENDING_DELETION" ->
              "status = 'PENDING_DELETION', deletion_due_at = now() + interval '15 days'";
          case "DELETED" ->
              "status = 'DELETED', deleted_at = now(), email = NULL, email_verified_at = NULL";
          default -> throw new IllegalArgumentException("不支持的状态: " + status);
        };
    jdbcTemplate.update("UPDATE idn_user SET " + assignments + " WHERE id = ?", id);
    // 绕过 JPA 改的列不会进一级缓存，清掉再读才是库里的值
    entityManager.clear();
    return id;
  }

  /// 合法的前缀。
  ///
  /// @param raw 前缀原文
  /// @return 前缀
  private static UsernamePrefix prefix(String raw) {
    return UsernamePrefix.parse(raw).orElseThrow();
  }

  /// 合法的昵称前缀。
  ///
  /// @param raw 前缀原文
  /// @return 前缀
  private static DisplayNamePrefix nickname(String raw) {
    return DisplayNamePrefix.parse(raw).orElseThrow();
  }
}
