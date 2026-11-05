package dev.patra.identity.app.account.usecase.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.service.PendingRegistrationCodes;
import dev.patra.identity.app.account.usecase.query.PendingRegistrationQueryService;
import dev.patra.identity.domain.account.exception.RegistrationCodeLimitedException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.enums.RegistrationCodePurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PendingCode;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.RegistrationCode;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.LoginMethodLinkCodeMail;
import dev.patra.identity.domain.account.policy.ProviderRegistrationPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.oauth.PendingRegistrationPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.RegistrationCodeThrottlePort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
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

/// SendProviderLinkCodeHandler 单元测试：有资格才发信，没资格时响应和存储都一样。
@DisplayName("SendProviderLinkCodeHandler 单元测试")
class SendProviderLinkCodeHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
  private static final String TICKET = "T".repeat(43);
  private static final EmailAddress EMAIL = EmailAddress.of("chen@example.com");

  private final PendingRegistrationPort pending = mock(PendingRegistrationPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final RegistrationCodeThrottlePort throttle = mock(RegistrationCodeThrottlePort.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final List<String> events = new ArrayList<>();
  private final SendProviderLinkCodeHandler handler =
      new SendProviderLinkCodeHandler(
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
          userReadPort,
          throttle,
          emailDelivery,
          new RecordingTransactions(events),
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static User user(UserStatus status, Instant emailVerifiedAt) {
    return User.restore()
        .id(42L)
        .email(EMAIL)
        .username(Username.of("chen-yu"))
        .emailVerifiedAt(emailVerifiedAt)
        .status(status)
        .version(0L)
        .build();
  }

  private static SendProviderLinkCodeCommand command() {
    return SendProviderLinkCodeCommand.of(TICKET, "Chen@Example.com", "203.0.113.9");
  }

  @BeforeEach
  void stubHappyPath() {
    when(pending.find(TICKET))
        .thenReturn(
            Optional.of(
                PendingRegistration.builder()
                    .provider(IdentityProvider.GITHUB)
                    .subject("583231")
                    .accountLabel("octocat")
                    .expiresAt(NOW.plus(Duration.ofMinutes(30)))
                    .build()));
    when(throttle.acquireForLink(any(), any(), any())).thenReturn(Optional.empty());
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
              events.add("store-code");
              return null;
            })
        .when(pending)
        .storeCode(any(), any(), any(), any());
    doAnswer(
            invocation -> {
              events.add("send");
              return null;
            })
        .when(emailDelivery)
        .send(any());
  }

  @Test
  @DisplayName("有资格：绑定用途的码写在事务外，邮件在短事务里，写明平台、第三方账号和要绑到的账号")
  void should_send_mail_when_eligible() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.ACTIVE, NOW)));

    RegistrationCodeSent sent = handler.handle(command());

    assertThat(sent.email()).isEqualTo("chen@example.com");
    assertThat(sent.codeExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    assertThat(sent.nextResendAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(events)
        .containsExactly(
            "store-code", RecordingTransactions.BEGIN, "send", RecordingTransactions.END);
    ArgumentCaptor<PendingCode> bound = ArgumentCaptor.forClass(PendingCode.class);
    ArgumentCaptor<RegistrationCode> code = ArgumentCaptor.forClass(RegistrationCode.class);
    verify(pending).storeCode(eq(TICKET), bound.capture(), code.capture(), any());
    assertThat(bound.getValue().purpose()).isEqualTo(RegistrationCodePurpose.LINK);
    assertThat(bound.getValue().username()).isNull();
    ArgumentCaptor<LoginMethodLinkCodeMail> mail =
        ArgumentCaptor.forClass(LoginMethodLinkCodeMail.class);
    verify(emailDelivery).send(mail.capture());
    assertThat(mail.getValue().code()).isEqualTo(code.getValue().value());
    assertThat(mail.getValue().to()).isEqualTo(EMAIL);
    assertThat(mail.getValue().displayName()).isEqualTo("陈宇");
    assertThat(mail.getValue().username()).isEqualTo("chen-yu");
    assertThat(mail.getValue().providerName()).isEqualTo("GitHub");
    assertThat(mail.getValue().accountLabel()).isEqualTo("octocat");
  }

  @Test
  @DisplayName("注销中的账号：和登录分流一致，照常发")
  void should_send_mail_when_pending_deletion() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION, NOW)));

    handler.handle(command());

    verify(emailDelivery).send(any());
  }

  @Test
  @DisplayName("没资格（邮箱未验证、已封禁、没注册）：响应一样，码也写，只是不发信，不开事务")
  void should_store_decoy_without_mail_when_not_eligible() {
    List<Optional<User>> ineligible =
        List.of(
            Optional.of(user(UserStatus.ACTIVE, null)),
            Optional.of(user(UserStatus.BANNED, NOW)),
            Optional.empty());

    for (Optional<User> found : ineligible) {
      when(users.findByEmail(EMAIL)).thenReturn(found);
      events.clear();

      RegistrationCodeSent sent = handler.handle(command());

      assertThat(sent.email()).isEqualTo("chen@example.com");
      assertThat(sent.codeExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
      assertThat(sent.nextResendAt()).isEqualTo(NOW.plusSeconds(60));
      assertThat(events).containsExactly("store-code");
    }
    verifyNoInteractions(emailDelivery);
  }

  @Test
  @DisplayName("限流排在判断资格之前：配额不因账号状态而不同")
  void should_throttle_before_checking_eligibility() {
    when(throttle.acquireForLink(any(), any(), any()))
        .thenReturn(Optional.of(Duration.ofSeconds(30)));

    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOf(RegistrationCodeLimitedException.class);

    verifyNoInteractions(users);
    verify(pending, never()).storeCode(any(), any(), any(), any());
  }

  @Test
  @DisplayName("邮箱格式不对：422，不消耗配额")
  void should_reject_invalid_email() {
    assertThatThrownBy(
            () ->
                handler.handle(
                    SendProviderLinkCodeCommand.of(TICKET, "not-an-email", "203.0.113.9")))
        .isInstanceOf(InvalidUserFieldsException.class);

    verifyNoInteractions(throttle);
  }
}
