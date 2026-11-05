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

import dev.patra.identity.app.account.usecase.query.PendingRegistrationQueryService;
import dev.patra.identity.domain.account.exception.RegistrationCodeLimitedException;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PendingCode;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.RegistrationCode;
import dev.patra.identity.domain.account.model.vo.mail.RegistrationCodeMail;
import dev.patra.identity.domain.account.policy.ProviderRegistrationPolicy;
import dev.patra.identity.domain.account.policy.UsernamePolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.oauth.PendingRegistrationPort;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
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

/// SendRegistrationCodeHandler 单元测试：校验顺序、验证码绑定的值、事务边界。
@DisplayName("SendRegistrationCodeHandler 单元测试")
class SendRegistrationCodeHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
  private static final String TICKET = "T".repeat(43);
  private static final EmailAddress EMAIL = EmailAddress.of("chen@example.com");

  private final PendingRegistrationPort pending = mock(PendingRegistrationPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final FormerUsernameRepository formerUsernames = mock(FormerUsernameRepository.class);
  private final RegistrationCodeThrottlePort throttle = mock(RegistrationCodeThrottlePort.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final List<String> events = new ArrayList<>();
  private final SendRegistrationCodeHandler handler =
      new SendRegistrationCodeHandler(
          new PendingRegistrationQueryService(pending),
          pending,
          users,
          formerUsernames,
          new UsernamePolicy(),
          ProviderRegistrationPolicy.builder()
              .pendingTtl(Duration.ofMinutes(30))
              .codeTtl(Duration.ofMinutes(10))
              .maxCodeAttempts(5)
              .resendCooldown(Duration.ofSeconds(60))
              .perEmailHourly(5)
              .perIpHourly(20)
              .build(),
          throttle,
          emailDelivery,
          new RecordingTransactions(events),
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static PendingRegistration pendingExpiringIn(Duration remaining) {
    return PendingRegistration.builder()
        .provider(IdentityProvider.GITHUB)
        .subject("583231")
        .accountLabel("octocat")
        .expiresAt(NOW.plus(remaining))
        .build();
  }

  private static SendRegistrationCodeCommand command() {
    return SendRegistrationCodeCommand.of(TICKET, " Chen-Yu ", "Chen@Example.com", "203.0.113.9");
  }

  @BeforeEach
  void stubHappyPath() {
    when(pending.find(TICKET)).thenReturn(Optional.of(pendingExpiringIn(Duration.ofMinutes(30))));
    when(throttle.acquire(any(), any(), any())).thenReturn(Optional.empty());
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
  @DisplayName("通过：写码在事务外，邮件在短事务里；码和邮件是同一个，绑定规范化之后的用户名与邮箱")
  void should_store_code_and_register_mail() {
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
    assertThat(bound.getValue().username()).isEqualTo("chen-yu");
    assertThat(bound.getValue().email()).isEqualTo("chen@example.com");
    ArgumentCaptor<RegistrationCodeMail> mail = ArgumentCaptor.forClass(RegistrationCodeMail.class);
    verify(emailDelivery).send(mail.capture());
    assertThat(mail.getValue().code()).isEqualTo(code.getValue().value());
    assertThat(mail.getValue().to()).isEqualTo(EMAIL);
  }

  @Test
  @DisplayName("验证码有效期不超过待补填记录的余量")
  void should_cap_code_ttl_by_pending_remaining() {
    when(pending.find(TICKET)).thenReturn(Optional.of(pendingExpiringIn(Duration.ofMinutes(3))));

    RegistrationCodeSent sent = handler.handle(command());

    assertThat(sent.codeExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3)));
    verify(pending).storeCode(eq(TICKET), any(), any(), eq(Duration.ofMinutes(3)));
  }

  @Test
  @DisplayName("邮箱已注册：409，不计限流、不发码")
  void should_not_send_or_count_when_email_taken() {
    when(users.existsByEmail(EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOf(UserIdentifierTakenException.class);

    verifyNoInteractions(throttle, emailDelivery);
    verify(pending, never()).storeCode(any(), any(), any(), any());
  }

  @Test
  @DisplayName("字段不合法：422，不查占用")
  void should_validate_fields_first() {
    SendRegistrationCodeCommand invalid = SendRegistrationCodeCommand.of(TICKET, "a", "x", null);

    assertThatThrownBy(() -> handler.handle(invalid))
        .isInstanceOf(InvalidUserFieldsException.class);

    verifyNoInteractions(users, throttle);
  }

  @Test
  @DisplayName("被限流：429，不写码")
  void should_not_store_when_limited() {
    when(throttle.acquire(any(), any(), any())).thenReturn(Optional.of(Duration.ofSeconds(30)));

    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOf(RegistrationCodeLimitedException.class);

    verify(pending, never()).storeCode(any(), any(), any(), any());
  }
}
