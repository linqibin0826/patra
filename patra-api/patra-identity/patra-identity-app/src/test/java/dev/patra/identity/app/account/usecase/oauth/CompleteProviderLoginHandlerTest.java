package dev.patra.identity.app.account.usecase.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.service.NewDeviceLoginNotifier;
import dev.patra.identity.domain.account.exception.UserBannedException;
import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.ProviderAccount;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.policy.ProviderRegistrationPolicy;
import dev.patra.identity.domain.account.policy.UsernamePolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.oauth.PendingRegistrationPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.policy.NewDeviceLoginPolicy;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.throttle.NewDeviceMailThrottlePort;
import dev.patra.identity.domain.session.service.SessionIssuer;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// CompleteProviderLoginHandler 单元测试：老用户发会话，新用户建待补填记录。
@DisplayName("CompleteProviderLoginHandler 单元测试")
class CompleteProviderLoginHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
  private static final String DEVICE = "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy";
  private static final ClientOrigin ORIGIN =
      ClientOrigin.builder()
          .ip("203.0.113.9")
          .userAgent("UA")
          .deviceName("Chrome 141 · macOS")
          .deviceClass(DeviceClass.DESKTOP)
          .ipRegion("澳大利亚")
          .build();
  private static final UserAccountReadModel ACCOUNT =
      UserAccountReadModel.builder()
          .userId(42L)
          .email("chen.yu@example.com")
          .emailVerified(true)
          .username("chen-yu")
          .displayName("chen-yu")
          .status(UserStatus.ACTIVE)
          .build();
  private static final ProviderAccount GITHUB =
      ProviderAccount.builder()
          .provider(IdentityProvider.GITHUB)
          .subject("583231")
          .accountLabel("OctoCat")
          .suggestedUsername("OctoCat")
          .build();

  private final ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
  private final PendingRegistrationPort pending = mock(PendingRegistrationPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
  private final ClientOriginResolver resolver = mock(ClientOriginResolver.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final NewDeviceMailThrottlePort throttle = mock(NewDeviceMailThrottlePort.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final List<String> events = new ArrayList<>();
  private final RecordingTransactions transactions = new RecordingTransactions(events);
  private final CompleteProviderLoginHandler handler =
      new CompleteProviderLoginHandler(
          identities,
          pending,
          users,
          userReadPort,
          sessionIssuer,
          resolver,
          new NewDeviceLoginNotifier(
              records,
              NewDeviceLoginPolicy.of(Duration.ofDays(180), Duration.ofHours(24)),
              throttle,
              emailDelivery,
              transactions),
          new UsernamePolicy(),
          ProviderRegistrationPolicy.builder()
              .pendingTtl(Duration.ofMinutes(30))
              .codeTtl(Duration.ofMinutes(10))
              .maxCodeAttempts(5)
              .resendCooldown(Duration.ofSeconds(60))
              .perEmailHourly(5)
              .perIpHourly(20)
              .build(),
          transactions,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static ExternalIdentity linked() {
    return ExternalIdentity.restore()
        .id(9L)
        .userId(42L)
        .provider(IdentityProvider.GITHUB)
        .subject("583231")
        .accountLabel("old-label")
        .linkedAt(NOW.minusSeconds(3600))
        .version(0L)
        .build();
  }

  private static User user(UserStatus status) {
    User.UserBuilder builder =
        User.restore()
            .id(42L)
            .email(EmailAddress.of("chen.yu@example.com"))
            .username(Username.of("chen-yu"))
            .status(status)
            .version(0L);
    if (status == UserStatus.BANNED) {
      builder.bannedAt(NOW);
    }
    if (status == UserStatus.PENDING_DELETION) {
      builder.deletionDueAt(NOW.plusSeconds(3600));
    }
    return builder.build();
  }

  private static CompleteProviderLoginCommand command(ProviderAccount account, String deviceId) {
    return CompleteProviderLoginCommand.builder()
        .account(account)
        .deviceId(deviceId)
        .clientIp("203.0.113.9")
        .userAgent("UA")
        .build();
  }

  @BeforeEach
  void stubHappyPath() {
    when(resolver.resolve(any())).thenReturn(ORIGIN);
    when(identities.findByProviderAndSubject(IdentityProvider.GITHUB, "583231"))
        .thenReturn(Optional.of(linked()));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
    when(sessionIssuer.issueWithoutPasswordProof(anyLong(), any(), any()))
        .thenReturn(IssuedUserSession.of("patra_user_x", List.of()));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
    when(records.existsTrustedDevice(eq(42L), any(), any())).thenReturn(true);
    when(pending.create(any(), any())).thenReturn("ticket-1");
  }

  @Test
  @DisplayName("老用户：发不带密码证明的会话，更新账号标识和最近登录时间")
  void should_issue_session_without_password_proof_for_linked_account() {
    ProviderLoginResult result = handler.handle(command(GITHUB, DEVICE));

    assertThat(result)
        .isInstanceOfSatisfying(
            ProviderLoginResult.Authenticated.class,
            authenticated -> {
              assertThat(authenticated.user().sessionToken()).isEqualTo("patra_user_x");
              assertThat(authenticated.user().account()).isEqualTo(ACCOUNT);
            });
    verify(sessionIssuer).issueWithoutPasswordProof(42L, LoginClient.of(null, DEVICE), ORIGIN);
    verify(sessionIssuer, never()).issue(anyLong(), any(), any());
    ArgumentCaptor<ExternalIdentity> saved = ArgumentCaptor.forClass(ExternalIdentity.class);
    verify(identities).save(saved.capture());
    assertThat(saved.getValue().getAccountLabel()).isEqualTo("OctoCat");
    assertThat(saved.getValue().getLastLoginAt()).isEqualTo(NOW);
    verifyNoInteractions(pending);
  }

  @Test
  @DisplayName("封禁账号：403，不发会话，不更新外部身份")
  void should_reject_banned_user() {
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(UserStatus.BANNED)));

    assertThatThrownBy(() -> handler.handle(command(GITHUB, DEVICE)))
        .isInstanceOf(UserBannedException.class);

    verify(sessionIssuer, never()).issueWithoutPasswordProof(anyLong(), any(), any());
    verify(identities, never()).save(any());
  }

  @Test
  @DisplayName("注销冷静期：照常发会话，门户据 status 进取消注销页")
  void should_issue_session_for_pending_deletion_user() {
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThat(handler.handle(command(GITHUB, DEVICE)))
        .isInstanceOf(ProviderLoginResult.Authenticated.class);
  }

  @Test
  @DisplayName("没见过的设备：提交后走新设备提醒，和密码登录一样")
  void should_notify_new_device() {
    when(records.existsTrustedDevice(eq(42L), any(), any())).thenReturn(false);
    when(throttle.acquire(eq(42L), any(), any())).thenReturn(true);

    handler.handle(command(GITHUB, DEVICE));

    verify(throttle).acquire(42L, new DeviceId(DEVICE), NOW);
    verify(emailDelivery).send(any());
  }

  @Test
  @DisplayName("设备标识格式不对：按没带处理，不报错（授权码已经用掉了）")
  void should_ignore_malformed_device_id() {
    assertThat(handler.handle(command(GITHUB, "not-a-device")))
        .isInstanceOf(ProviderLoginResult.Authenticated.class);

    verify(sessionIssuer).issueWithoutPasswordProof(42L, LoginClient.web(), ORIGIN);
  }

  @Test
  @DisplayName("新用户：建待补填记录，不写库、不发会话；预填用户名转小写")
  void should_create_pending_registration_for_unknown_account() {
    when(identities.findByProviderAndSubject(IdentityProvider.GITHUB, "583231"))
        .thenReturn(Optional.empty());

    ProviderLoginResult result = handler.handle(command(GITHUB, DEVICE));

    assertThat(result)
        .isInstanceOfSatisfying(
            ProviderLoginResult.RegistrationRequired.class,
            required -> {
              assertThat(required.ticket()).isEqualTo("ticket-1");
              PendingRegistration registration = required.pending();
              assertThat(registration.provider()).isEqualTo(IdentityProvider.GITHUB);
              assertThat(registration.subject()).isEqualTo("583231");
              assertThat(registration.accountLabel()).isEqualTo("OctoCat");
              assertThat(registration.suggestedUsername()).isEqualTo("octocat");
              assertThat(registration.suggestedEmail()).isNull();
              assertThat(registration.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
            });
    verify(pending).create(any(), eq(Duration.ofMinutes(30)));
    verifyNoInteractions(users, sessionIssuer);
  }

  @Test
  @DisplayName("预填用户名过不了规则就留空：纯数字、结尾是连字符、保留字")
  void should_drop_suggested_username_that_breaks_the_rules() {
    when(identities.findByProviderAndSubject(any(), any())).thenReturn(Optional.empty());

    for (String login : List.of("12345", "foo-", "admin", "ab")) {
      ProviderAccount account =
          ProviderAccount.builder()
              .provider(IdentityProvider.GITHUB)
              .subject("1")
              .accountLabel(login)
              .suggestedUsername(login)
              .build();

      ProviderLoginResult result = handler.handle(command(account, null));

      assertThat(((ProviderLoginResult.RegistrationRequired) result).pending().suggestedUsername())
          .as(login)
          .isNull();
    }
  }

  @Test
  @DisplayName("预填邮箱规范化；不合法的留空")
  void should_normalize_suggested_email() {
    when(identities.findByProviderAndSubject(any(), any())).thenReturn(Optional.empty());
    ProviderAccount valid =
        ProviderAccount.builder()
            .provider(IdentityProvider.GOOGLE)
            .subject("1")
            .accountLabel("Chen@Example.com")
            .suggestedEmail("Chen@Example.com")
            .build();
    ProviderAccount invalid =
        ProviderAccount.builder()
            .provider(IdentityProvider.GOOGLE)
            .subject("2")
            .accountLabel("x")
            .suggestedEmail("not-an-email")
            .build();

    ProviderLoginResult first = handler.handle(command(valid, null));
    ProviderLoginResult second = handler.handle(command(invalid, null));

    assertThat(((ProviderLoginResult.RegistrationRequired) first).pending().suggestedEmail())
        .isEqualTo("chen@example.com");
    assertThat(((ProviderLoginResult.RegistrationRequired) second).pending().suggestedEmail())
        .isNull();
  }
}
