package dev.patra.identity.app.account.usecase.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.service.PendingRegistrationCodes;
import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.app.account.usecase.query.PendingRegistrationQueryService;
import dev.patra.identity.domain.account.exception.ProviderAccountTakenException;
import dev.patra.identity.domain.account.exception.ProviderAlreadyLinkedException;
import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.enums.RegistrationCodePurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.CodeCheck;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.LoginMethodLinkedMail;
import dev.patra.identity.domain.account.policy.ProviderRegistrationPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.oauth.PendingRegistrationPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
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

/// LinkPendingRegistrationHandler 单元测试：校验码在事务外，锁内复核资格，撞车不写，会话不带密码证明。
@DisplayName("LinkPendingRegistrationHandler 单元测试")
class LinkPendingRegistrationHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
  private static final String TICKET = "T".repeat(43);
  private static final String SUBJECT = "583231";
  private static final EmailAddress EMAIL = EmailAddress.of("chen@example.com");
  private static final CodeCheck MATCHED = new CodeCheck.Matched(null, EMAIL.value());
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
  private final ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
  private final ClientOriginResolver resolver = mock(ClientOriginResolver.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final List<String> events = new ArrayList<>();
  private final LinkPendingRegistrationHandler handler =
      new LinkPendingRegistrationHandler(
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
          identities,
          userReadPort,
          sessionIssuer,
          resolver,
          emailDelivery,
          new RecordingTransactions(events),
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static User user(UserStatus status, Instant emailVerifiedAt, String email) {
    return User.restore()
        .id(42L)
        .email(EmailAddress.of(email))
        .username(Username.of("chen-yu"))
        .emailVerifiedAt(emailVerifiedAt)
        .status(status)
        .version(0L)
        .build();
  }

  private static User eligible() {
    return user(UserStatus.ACTIVE, NOW, EMAIL.value());
  }

  private static ExternalIdentity identityOf(long userId) {
    return ExternalIdentity.link(userId, IdentityProvider.GITHUB, SUBJECT, "octocat", NOW);
  }

  private static LinkPendingRegistrationCommand command(String code) {
    return LinkPendingRegistrationCommand.builder().ticket(TICKET).code(code).build();
  }

  private void assertCodeInvalid(String code) {
    assertThatThrownBy(() -> handler.handle(command(code)))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .singleElement()
                    .satisfies(
                        violation -> {
                          assertThat(violation.field()).isEqualTo("code");
                          assertThat(violation.code()).isEqualTo("INVALID");
                        }));
  }

  @BeforeEach
  void stubHappyPath() {
    when(pending.find(TICKET))
        .thenReturn(
            Optional.of(
                PendingRegistration.builder()
                    .provider(IdentityProvider.GITHUB)
                    .subject(SUBJECT)
                    .accountLabel("octocat")
                    .expiresAt(NOW.plus(Duration.ofMinutes(30)))
                    .build()));
    when(pending.verifyCode(eq(TICKET), any(), eq(RegistrationCodePurpose.LINK), eq(5)))
        .thenAnswer(
            invocation -> {
              events.add("verify");
              return MATCHED;
            });
    when(resolver.resolve(any())).thenReturn(ORIGIN);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(eligible()));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(eligible()));
    when(identities.findByProviderAndSubject(IdentityProvider.GITHUB, SUBJECT))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(identityOf(42L)));
    when(identities.findByUserIdAndProvider(42L, IdentityProvider.GITHUB))
        .thenReturn(Optional.empty());
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
                    .email(EMAIL.value())
                    .emailVerified(true)
                    .username("chen-yu")
                    .displayName("陈宇")
                    .status(UserStatus.ACTIVE)
                    .build()));
    doAnswer(
            invocation -> {
              events.add("send");
              return null;
            })
        .when(emailDelivery)
        .send(any());
    doAnswer(
            invocation -> {
              events.add("delete-pending");
              return null;
            })
        .when(pending)
        .delete(TICKET);
  }

  @Test
  @DisplayName("通过：校验码在事务外；绑定、登记邮件、发会话在事务里，会话最后发；提交后才删待补填记录")
  void should_verify_outside_and_link_inside_transaction() {
    AuthenticatedUser result = handler.handle(command("483920"));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    assertThat(result.account().userId()).isEqualTo(42L);
    assertThat(events)
        .containsExactly(
            "verify",
            RecordingTransactions.BEGIN,
            "send",
            "session",
            RecordingTransactions.END,
            "delete-pending");
    ArgumentCaptor<ExternalIdentity> linked = ArgumentCaptor.forClass(ExternalIdentity.class);
    verify(identities).insertIfAbsent(linked.capture());
    assertThat(linked.getValue().getUserId()).isEqualTo(42L);
    verify(sessionIssuer, never()).issue(anyLong(), any(), any());
    ArgumentCaptor<LoginMethodLinkedMail> mail =
        ArgumentCaptor.forClass(LoginMethodLinkedMail.class);
    verify(emailDelivery).send(mail.capture());
    assertThat(mail.getValue().to()).isEqualTo(EMAIL);
    assertThat(mail.getValue().accountLabel()).isEqualTo("octocat");
  }

  @Test
  @DisplayName("验证码不对：422 INVALID，什么都不碰")
  void should_not_link_with_wrong_code() {
    when(pending.verifyCode(eq(TICKET), any(), eq(RegistrationCodePurpose.LINK), eq(5)))
        .thenReturn(new CodeCheck.Mismatch());

    assertCodeInvalid("000000");

    verifyNoInteractions(users, identities, sessionIssuer, emailDelivery);
  }

  @Test
  @DisplayName("事务外查到的账号没资格：和验证码不对一样回 INVALID")
  void should_answer_invalid_when_not_eligible() {
    List<Optional<User>> ineligible =
        List.of(
            Optional.of(user(UserStatus.ACTIVE, null, EMAIL.value())),
            Optional.of(user(UserStatus.BANNED, NOW, EMAIL.value())),
            Optional.empty());

    for (Optional<User> found : ineligible) {
      when(users.findByEmail(EMAIL)).thenReturn(found);

      assertCodeInvalid("483920");
    }

    verify(identities, never()).insertIfAbsent(any());
    verifyNoInteractions(sessionIssuer, emailDelivery);
  }

  @Test
  @DisplayName("锁内复核：发码之后被封禁或换了邮箱，都不绑")
  void should_recheck_eligibility_under_lock() {
    List<User> changed =
        List.of(
            user(UserStatus.BANNED, NOW, EMAIL.value()),
            user(UserStatus.ACTIVE, NOW, "other@example.com"));

    for (User locked : changed) {
      when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(locked));

      assertCodeInvalid("483920");
    }

    verify(identities, never()).insertIfAbsent(any());
    verifyNoInteractions(sessionIssuer, emailDelivery);
  }

  @Test
  @DisplayName("第三方账号已经绑给别人：PROVIDER_ACCOUNT_TAKEN")
  void should_reject_when_taken_by_someone_else() {
    when(identities.findByProviderAndSubject(IdentityProvider.GITHUB, SUBJECT))
        .thenReturn(Optional.of(identityOf(7L)));

    assertThatThrownBy(() -> handler.handle(command("483920")))
        .isInstanceOf(ProviderAccountTakenException.class);

    verifyNoInteractions(sessionIssuer, emailDelivery);
  }

  @Test
  @DisplayName("并发抢先：插入没成功按 PROVIDER_ACCOUNT_TAKEN")
  void should_reject_when_insert_loses_race() {
    when(identities.insertIfAbsent(any())).thenReturn(false);

    assertThatThrownBy(() -> handler.handle(command("483920")))
        .isInstanceOf(ProviderAccountTakenException.class);

    verifyNoInteractions(sessionIssuer, emailDelivery);
  }

  @Test
  @DisplayName("这个账号已经绑过同平台的另一个账号：PROVIDER_ALREADY_LINKED")
  void should_reject_when_target_already_linked() {
    when(identities.findByUserIdAndProvider(42L, IdentityProvider.GITHUB))
        .thenReturn(Optional.of(identityOf(42L)));

    assertThatThrownBy(() -> handler.handle(command("483920")))
        .isInstanceOf(ProviderAlreadyLinkedException.class);

    verify(identities, never()).insertIfAbsent(any());
    verifyNoInteractions(sessionIssuer, emailDelivery);
  }

  @Test
  @DisplayName("已经绑到这个账号：按登录处理，不插入，不再发信")
  void should_treat_own_link_as_login() {
    when(identities.findByProviderAndSubject(IdentityProvider.GITHUB, SUBJECT))
        .thenReturn(Optional.of(identityOf(42L)));

    AuthenticatedUser result = handler.handle(command("483920"));

    assertThat(result.sessionToken()).isEqualTo("patra_user_x");
    verify(identities, never()).insertIfAbsent(any());
    verifyNoInteractions(emailDelivery);
  }
}
