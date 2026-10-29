package dev.patra.identity.app.account.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.UserNotFoundException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.FormerUsername;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.PublicAudience;
import dev.patra.identity.domain.account.model.read.PublicUserLookup;
import dev.patra.identity.domain.account.model.read.PublicUserReadModel;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.account.policy.ProfileDisplayPolicy;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// UserQueryService 单元测试：当前用户与公开身份卡。
@DisplayName("UserQueryService 单元测试")
class UserQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");
  private static final CurrentUser OWNER =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);
  private static final CurrentUser OTHER =
      CurrentUser.of(99L, 7002L, AccountType.USER, ClientType.WEB);
  private static final Username CHEN_YU = Username.of("chen-yu");

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final FormerUsernameRepository formerUsernames = mock(FormerUsernameRepository.class);
  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final UserProfileRepository profiles = mock(UserProfileRepository.class);
  private final UserQueryService service =
      new UserQueryService(
          currentUserPort,
          userReadPort,
          formerUsernames,
          tokens,
          profiles,
          ProfileDisplayPolicy.of(ZoneOffset.UTC),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 一个账号读模型。
  ///
  /// @param status 状态
  /// @return 读模型
  private static UserAccountReadModel account(UserStatus status) {
    return UserAccountReadModel.builder()
        .userId(42L)
        .email(status == UserStatus.DELETED ? null : "chen.yu@example.com")
        .emailVerified(false)
        .username("chen-yu")
        .displayName("chen-yu")
        .status(status)
        .deletionDueAt(status == UserStatus.PENDING_DELETION ? NOW.plus(Duration.ofDays(15)) : null)
        .build();
  }

  /// 一个公开身份卡读模型。
  ///
  /// @param username 用户名
  /// @param status 状态
  /// @param emailVerified 邮箱是否已验证
  /// @return 读模型
  private static PublicUserReadModel publicUser(
      String username, UserStatus status, boolean emailVerified) {
    return PublicUserReadModel.builder()
        .userId(42L)
        .username(username)
        .displayName("陈宇")
        .status(status)
        .emailVerified(emailVerified)
        .build();
  }

  @Test
  @DisplayName("有当前用户且账号正常：返回读模型")
  void should_return_current_account() {
    when(currentUserPort.require()).thenReturn(OWNER);
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(account(UserStatus.ACTIVE)));

    UserAccountReadModel account = service.currentAccount();

    assertThat(account.userId()).isEqualTo(42L);
    assertThat(account.email()).isEqualTo("chen.yu@example.com");
  }

  @Test
  @DisplayName("冷静期内：照常返回，带到期时间")
  void should_return_pending_deletion_account_with_due_time() {
    when(currentUserPort.require()).thenReturn(OWNER);
    when(userReadPort.findAccount(42L))
        .thenReturn(Optional.of(account(UserStatus.PENDING_DELETION)));

    UserAccountReadModel account = service.currentAccount();

    assertThat(account.status()).isEqualTo(UserStatus.PENDING_DELETION);
    assertThat(account.deletionDueAt()).isEqualTo(NOW.plus(Duration.ofDays(15)));
  }

  @Test
  @DisplayName("待确认的改邮箱：有未过期的确认令牌就返回新邮箱与到期时间，没有为空")
  void should_expose_pending_email_change_from_active_confirm_token() {
    when(currentUserPort.require()).thenReturn(OWNER);
    EmailToken token =
        EmailToken.issue(
            EmailTokenPurpose.EMAIL_CHANGE_CONFIRM,
            42L,
            EmailAddress.of("new@example.com"),
            new EmailTokenSecret("Q".repeat(43)),
            NOW,
            null);
    when(tokens.findActive(42L, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW))
        .thenReturn(Optional.of(token))
        .thenReturn(Optional.empty());

    assertThat(service.pendingEmailChange())
        .contains(PendingEmailChange.of("new@example.com", token.getExpiresAt()));
    assertThat(service.pendingEmailChange()).isEmpty();
  }

  @Test
  @DisplayName("没有当前用户：401，不查库")
  void should_require_authentication() {
    when(currentUserPort.require()).thenThrow(new AuthenticationRequiredException());

    assertThatThrownBy(service::currentAccount).isInstanceOf(AuthenticationRequiredException.class);
    verifyNoInteractions(userReadPort);
  }

  @Test
  @DisplayName("会话指向的用户不存在：401")
  void should_reject_missing_user() {
    when(currentUserPort.require()).thenReturn(OWNER);
    when(userReadPort.findAccount(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(service::currentAccount).isInstanceOf(AuthenticationRequiredException.class);
  }

  @Test
  @DisplayName("用户已封禁或已注销但断言还没过期：401")
  void should_reject_banned_or_deleted_user() {
    when(currentUserPort.require()).thenReturn(OWNER);
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(account(UserStatus.BANNED)));
    assertThatThrownBy(service::currentAccount).isInstanceOf(AuthenticationRequiredException.class);

    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(account(UserStatus.DELETED)));
    assertThatThrownBy(service::currentAccount).isInstanceOf(AuthenticationRequiredException.class);
  }

  @Test
  @DisplayName("匿名访客看正常且已验证的用户：Found，audience 是 PUBLIC")
  void should_return_found_with_public_audience_for_visible_user() {
    when(currentUserPort.current()).thenReturn(Optional.empty());
    when(userReadPort.findPublicIdentity(CHEN_YU))
        .thenReturn(Optional.of(publicUser("chen-yu", UserStatus.ACTIVE, true)));

    PublicUserLookup lookup = service.publicIdentity("chen-yu");

    assertThat(lookup)
        .isInstanceOfSatisfying(
            PublicUserLookup.Found.class,
            found -> {
              assertThat(found.view().displayName()).isEqualTo("陈宇");
              assertThat(found.view().audience()).isEqualTo(PublicAudience.PUBLIC);
            });
  }

  @Test
  @DisplayName("别人看未验证、冷静期、封禁的用户：404，匿名和登录一样")
  void should_throw_404_for_unverified_or_non_active_user_when_requested_by_others() {
    PublicUserReadModel[] hidden = {
      publicUser("chen-yu", UserStatus.ACTIVE, false),
      publicUser("chen-yu", UserStatus.PENDING_DELETION, true),
      publicUser("chen-yu", UserStatus.BANNED, true)
    };
    for (PublicUserReadModel user : hidden) {
      when(userReadPort.findPublicIdentity(CHEN_YU)).thenReturn(Optional.of(user));
      when(currentUserPort.current()).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.publicIdentity("chen-yu"))
          .isInstanceOf(UserNotFoundException.class);
      when(currentUserPort.current()).thenReturn(Optional.of(OTHER));
      assertThatThrownBy(() -> service.publicIdentity("chen-yu"))
          .isInstanceOf(UserNotFoundException.class);
    }
  }

  @Test
  @DisplayName("本人看自己：未验证或冷静期内也 Found，audience 是 SELF")
  void should_return_found_with_self_audience_for_owner() {
    when(currentUserPort.current()).thenReturn(Optional.of(OWNER));
    when(userReadPort.findPublicIdentity(CHEN_YU))
        .thenReturn(Optional.of(publicUser("chen-yu", UserStatus.ACTIVE, false)));
    assertThat(service.publicIdentity("chen-yu"))
        .isInstanceOfSatisfying(
            PublicUserLookup.Found.class,
            found -> assertThat(found.view().audience()).isEqualTo(PublicAudience.SELF));

    when(userReadPort.findPublicIdentity(CHEN_YU))
        .thenReturn(Optional.of(publicUser("chen-yu", UserStatus.PENDING_DELETION, true)));
    assertThat(service.publicIdentity("chen-yu")).isInstanceOf(PublicUserLookup.Found.class);
  }

  @Test
  @DisplayName("保留期内的旧名：Moved 到原主的当前用户名")
  void should_return_moved_for_reserved_former_username() {
    Username old = Username.of("chen-old");
    when(currentUserPort.current()).thenReturn(Optional.empty());
    when(userReadPort.findPublicIdentity(old)).thenReturn(Optional.empty());
    when(formerUsernames.findReserved(old, NOW))
        .thenReturn(
            Optional.of(
                FormerUsername.restore(
                    1L,
                    42L,
                    old,
                    NOW.minus(Duration.ofDays(1)),
                    NOW.plus(Duration.ofDays(89)),
                    0L)));
    when(userReadPort.findPublicIdentityByUserId(42L))
        .thenReturn(Optional.of(publicUser("chen-yu", UserStatus.ACTIVE, true)));

    PublicUserLookup lookup = service.publicIdentity("chen-old");

    assertThat(lookup)
        .isInstanceOfSatisfying(
            PublicUserLookup.Moved.class, moved -> assertThat(moved.current()).isEqualTo(CHEN_YU));
  }

  @Test
  @DisplayName("本人用自己的旧名：未验证也 Moved")
  void should_redirect_owner_requesting_own_former_username_even_when_unverified() {
    Username old = Username.of("chen-old");
    when(currentUserPort.current()).thenReturn(Optional.of(OWNER));
    when(userReadPort.findPublicIdentity(old)).thenReturn(Optional.empty());
    when(formerUsernames.findReserved(old, NOW))
        .thenReturn(
            Optional.of(
                FormerUsername.restore(
                    1L,
                    42L,
                    old,
                    NOW.minus(Duration.ofDays(1)),
                    NOW.plus(Duration.ofDays(89)),
                    0L)));
    when(userReadPort.findPublicIdentityByUserId(42L))
        .thenReturn(Optional.of(publicUser("chen-yu", UserStatus.ACTIVE, false)));

    assertThat(service.publicIdentity("chen-old")).isInstanceOf(PublicUserLookup.Moved.class);
  }

  @Test
  @DisplayName("跳转目标对请求者不可见：404")
  void should_throw_404_when_redirect_target_is_not_visible() {
    Username old = Username.of("chen-old");
    when(currentUserPort.current()).thenReturn(Optional.empty());
    when(userReadPort.findPublicIdentity(old)).thenReturn(Optional.empty());
    when(formerUsernames.findReserved(old, NOW))
        .thenReturn(
            Optional.of(
                FormerUsername.restore(
                    1L,
                    42L,
                    old,
                    NOW.minus(Duration.ofDays(1)),
                    NOW.plus(Duration.ofDays(89)),
                    0L)));
    when(userReadPort.findPublicIdentityByUserId(42L))
        .thenReturn(Optional.of(publicUser("chen-yu", UserStatus.ACTIVE, false)));

    assertThatThrownBy(() -> service.publicIdentity("chen-old"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  @DisplayName("旧名已过保留期或从没存在过：404")
  void should_throw_404_when_former_username_expired() {
    when(currentUserPort.current()).thenReturn(Optional.empty());
    when(userReadPort.findPublicIdentity(Username.of("chen-old"))).thenReturn(Optional.empty());
    when(formerUsernames.findReserved(Username.of("chen-old"), NOW)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.publicIdentity("chen-old"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  @DisplayName("路径里的用户名语法不合法：404，不查库")
  void should_return_404_for_syntactically_invalid_username_without_querying() {
    assertThatThrownBy(() -> service.publicIdentity("Chen Yu"))
        .isInstanceOf(UserNotFoundException.class);
    assertThatThrownBy(() -> service.publicIdentity("ab"))
        .isInstanceOf(UserNotFoundException.class);
    verifyNoInteractions(userReadPort, formerUsernames);
  }

  @Test
  @DisplayName("路径里的用户名带大写或首尾空白：折成小写后查")
  void should_fold_path_username_to_lowercase() {
    when(currentUserPort.current()).thenReturn(Optional.empty());
    when(userReadPort.findPublicIdentity(CHEN_YU))
        .thenReturn(Optional.of(publicUser("chen-yu", UserStatus.ACTIVE, true)));

    assertThat(service.publicIdentity(" Chen-Yu ")).isInstanceOf(PublicUserLookup.Found.class);
    verify(userReadPort).findPublicIdentity(CHEN_YU);
  }

  @Test
  @DisplayName("本人资料：冷静期照常返回，封禁 401")
  void should_return_own_profile_unless_banned() {
    when(currentUserPort.require()).thenReturn(OWNER);
    UserProfile profile = UserProfile.restore().id(1L).userId(42L).version(0L).build();
    when(profiles.findByUserId(42L)).thenReturn(Optional.of(profile));
    when(userReadPort.findAccount(42L))
        .thenReturn(Optional.of(account(UserStatus.PENDING_DELETION)));

    assertThat(service.myProfile()).isSameAs(profile);
    assertThat(service.myPrivacy()).isEqualTo(PrivacySettings.defaults());

    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(account(UserStatus.BANNED)));
    assertThatThrownBy(service::myProfile).isInstanceOf(AuthenticationRequiredException.class);
    assertThatThrownBy(service::myPrivacy).isInstanceOf(AuthenticationRequiredException.class);
  }

  @Test
  @DisplayName("主页仅登录用户：匿名 404，别人 Found 且 audience 是 SIGNED_IN")
  void should_hide_signed_in_page_from_anonymous() {
    when(userReadPort.findPublicIdentity(CHEN_YU))
        .thenReturn(Optional.of(withVisibility(ProfileVisibility.SIGNED_IN)));

    when(currentUserPort.current()).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.publicIdentity("chen-yu"))
        .isInstanceOf(UserNotFoundException.class);

    when(currentUserPort.current()).thenReturn(Optional.of(OTHER));
    assertThat(service.publicIdentity("chen-yu"))
        .isInstanceOfSatisfying(
            PublicUserLookup.Found.class,
            found -> assertThat(found.view().audience()).isEqualTo(PublicAudience.SIGNED_IN));
  }

  @Test
  @DisplayName("主页仅自己：别人 404，本人 Found")
  void should_hide_self_only_page_from_others() {
    when(userReadPort.findPublicIdentity(CHEN_YU))
        .thenReturn(Optional.of(withVisibility(ProfileVisibility.SELF)));

    when(currentUserPort.current()).thenReturn(Optional.of(OTHER));
    assertThatThrownBy(() -> service.publicIdentity("chen-yu"))
        .isInstanceOf(UserNotFoundException.class);

    when(currentUserPort.current()).thenReturn(Optional.of(OWNER));
    assertThat(service.publicIdentity("chen-yu")).isInstanceOf(PublicUserLookup.Found.class);
  }

  @Test
  @DisplayName("旧名的原主仅登录可见：匿名 404，登录者 Moved")
  void should_apply_visibility_to_former_username_owner() {
    Username old = Username.of("chen-old");
    when(userReadPort.findPublicIdentity(old)).thenReturn(Optional.empty());
    when(formerUsernames.findReserved(old, NOW))
        .thenReturn(
            Optional.of(
                FormerUsername.restore(
                    1L,
                    42L,
                    old,
                    NOW.minus(Duration.ofDays(1)),
                    NOW.plus(Duration.ofDays(89)),
                    0L)));
    when(userReadPort.findPublicIdentityByUserId(42L))
        .thenReturn(Optional.of(withVisibility(ProfileVisibility.SIGNED_IN)));

    when(currentUserPort.current()).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.publicIdentity("chen-old"))
        .isInstanceOf(UserNotFoundException.class);

    when(currentUserPort.current()).thenReturn(Optional.of(OTHER));
    assertThat(service.publicIdentity("chen-old")).isInstanceOf(PublicUserLookup.Moved.class);
  }

  /// 正常、已验证、主页可见性为给定值的公开主页读模型。
  ///
  /// @param visibility 主页可见性
  /// @return 读模型
  private static PublicUserReadModel withVisibility(ProfileVisibility visibility) {
    return publicUser("chen-yu", UserStatus.ACTIVE, true).toBuilder()
        .privacy(PrivacySettings.of(visibility, ProfessionalVisibility.PUBLIC, false))
        .build();
  }
}
