package dev.patra.identity.app.account.usecase.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.service.PendingRegistrationCodes;
import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.app.account.usecase.query.PendingRegistrationQueryService;
import dev.patra.identity.domain.account.exception.ProviderAccountTakenException;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.enums.RegistrationCodePurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.CodeCheck;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.policy.ProviderRegistrationPolicy;
import dev.patra.identity.domain.account.port.oauth.PendingRegistrationPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.service.SessionIssuer;
import dev.patra.tool.core.error.field.FieldViolation;
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

/// CompleteProviderRegistrationHandler 单元测试：验证码通过才建账号，Redis 操作都在事务外。
@DisplayName("CompleteProviderRegistrationHandler 单元测试")
class CompleteProviderRegistrationHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
  private static final String TICKET = "T".repeat(43);
  private static final PendingRegistration PENDING =
      PendingRegistration.builder()
          .provider(IdentityProvider.GITHUB)
          .subject("583231")
          .accountLabel("octocat")
          .expiresAt(NOW.plus(Duration.ofMinutes(30)))
          .build();
  private static final CodeCheck MATCHED = new CodeCheck.Matched("chen-yu", "chen@example.com");
  private static final ClientOrigin ORIGIN =
      ClientOrigin.builder()
          .ip("203.0.113.9")
          .userAgent("UA")
          .deviceName("Chrome 141 · macOS")
          .deviceClass(DeviceClass.DESKTOP)
          .ipRegion("澳大利亚")
          .build();

  private final PendingRegistrationPort pending = mock(PendingRegistrationPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserProfileRepository profiles = mock(UserProfileRepository.class);
  private final FormerUsernameRepository formerUsernames = mock(FormerUsernameRepository.class);
  private final ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
  private final ClientOriginResolver resolver = mock(ClientOriginResolver.class);
  private final List<String> events = new ArrayList<>();
  private final CompleteProviderRegistrationHandler handler =
      new CompleteProviderRegistrationHandler(
          new PendingRegistrationQueryService(pending),
          new PendingRegistrationCodes(
              pending,
              ProviderRegistrationPolicy.builder()
                  .pendingTtl(Duration.ofMinutes(30))
                  .codeTtl(Duration.ofMinutes(10))
                  .maxCodeAttempts(5)
                  .resendCooldown(Duration.ofSeconds(60))
                  .perEmailHourly(5)
                  .perIpHourly(20)
                  .linkPerEmailDaily(10)
                  .build()),
          users,
          profiles,
          formerUsernames,
          identities,
          userReadPort,
          sessionIssuer,
          resolver,
          new RecordingTransactions(events),
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static CompleteProviderRegistrationCommand command(String code) {
    return CompleteProviderRegistrationCommand.builder().ticket(TICKET).code(code).build();
  }

  private static User saved(User user) {
    return User.restore()
        .id(42L)
        .email(user.getEmail().orElseThrow())
        .username(user.getUsername())
        .emailVerifiedAt(NOW)
        .status(UserStatus.ACTIVE)
        .version(0L)
        .build();
  }

  @BeforeEach
  void stubHappyPath() {
    when(pending.find(TICKET)).thenReturn(Optional.of(PENDING));
    when(pending.verifyCode(eq(TICKET), any(), eq(RegistrationCodePurpose.REGISTER), eq(5)))
        .thenAnswer(
            invocation -> {
              events.add("verify");
              return MATCHED;
            });
    when(resolver.resolve(any())).thenReturn(ORIGIN);
    when(users.save(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));
    when(identities.insertIfAbsent(any())).thenReturn(true);
    when(sessionIssuer.issueWithoutPasswordProof(anyLong(), any(), any()))
        .thenAnswer(
            invocation -> {
              events.add("session");
              return IssuedUserSession.of("patra_user_x", List.of());
            });
    when(userReadPort.findAccount(42L))
        .thenReturn(
            Optional.of(
                UserAccountReadModel.builder()
                    .userId(42L)
                    .email("chen@example.com")
                    .emailVerified(true)
                    .username("chen-yu")
                    .displayName("chen-yu")
                    .status(UserStatus.ACTIVE)
                    .build()));
    doAnswer(
            invocation -> {
              events.add("delete-pending");
              return null;
            })
        .when(pending)
        .delete(TICKET);
  }

  @Test
  @DisplayName("通过：校验码在事务外，建账号和发会话在事务里，提交后才删待补填记录")
  void should_verify_outside_and_create_inside_transaction() {
    AuthenticatedUser result = handler.handle(command("483920"));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    assertThat(events)
        .containsExactly(
            "verify",
            RecordingTransactions.BEGIN,
            "session",
            RecordingTransactions.END,
            "delete-pending");
  }

  @Test
  @DisplayName("通过：用户名和邮箱来自发码时绑定的值，邮箱记为已验证")
  void should_create_verified_user_from_values_bound_to_code() {
    handler.handle(command("483920"));

    ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
    verify(users).save(created.capture());
    assertThat(created.getValue().getEmail()).contains(EmailAddress.of("chen@example.com"));
    assertThat(created.getValue().getUsername()).isEqualTo(Username.of("chen-yu"));
    assertThat(created.getValue().isEmailVerified()).isTrue();
    verify(sessionIssuer, never()).issue(anyLong(), any(), any());
  }

  @Test
  @DisplayName("验证码不对：422 INVALID，什么都不写")
  void should_reject_wrong_code() {
    when(pending.verifyCode(eq(TICKET), any(), eq(RegistrationCodePurpose.REGISTER), eq(5)))
        .thenReturn(new CodeCheck.Mismatch());

    assertThatThrownBy(() -> handler.handle(command("483920")))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .extracting(FieldViolation::code)
                    .containsExactly("INVALID"));
    verifyNoInteractions(users, identities, sessionIssuer);
  }

  @Test
  @DisplayName("没有可用的验证码：422 EXPIRED，门户提示重发")
  void should_report_expired_when_no_active_code() {
    when(pending.verifyCode(eq(TICKET), any(), eq(RegistrationCodePurpose.REGISTER), eq(5)))
        .thenReturn(new CodeCheck.NoActiveCode());

    assertThatThrownBy(() -> handler.handle(command("483920")))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .extracting(FieldViolation::code)
                    .containsExactly("EXPIRED"));
  }

  @Test
  @DisplayName("格式不对：不查待补填、不花错误次数")
  void should_not_spend_attempt_on_malformed_code() {
    assertThatThrownBy(() -> handler.handle(command("12ab")))
        .isInstanceOf(InvalidUserFieldsException.class);

    verify(pending, never()).verifyCode(anyString(), any(), any(), eq(5));
  }

  @Test
  @DisplayName("发码之后邮箱被抢：409，不存用户")
  void should_reject_when_email_taken_after_code_sent() {
    when(users.existsByEmail(EmailAddress.of("chen@example.com"))).thenReturn(true);

    assertThatThrownBy(() -> handler.handle(command("483920")))
        .isInstanceOf(UserIdentifierTakenException.class);

    verify(users, never()).save(any());
  }

  @Test
  @DisplayName("第三方账号已被注册：409，不发会话，待补填记录不删")
  void should_reject_when_provider_account_taken() {
    when(identities.insertIfAbsent(any())).thenReturn(false);

    assertThatThrownBy(() -> handler.handle(command("483920")))
        .isInstanceOf(ProviderAccountTakenException.class);

    verify(sessionIssuer, never()).issueWithoutPasswordProof(anyLong(), any(), any());
    verify(pending, never()).delete(any());
  }

  @Test
  @DisplayName("删待补填记录失败只记日志：账号已经建好了")
  void should_not_fail_when_deleting_pending_fails() {
    doThrow(new IllegalStateException("redis")).when(pending).delete(TICKET);

    assertThat(handler.handle(command("483920")).sessionToken()).isEqualTo("patra_user_x");
  }
}
