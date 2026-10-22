package dev.patra.identity.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.model.aggregate.EmailToken;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.policy.MailLinkPolicy;
import dev.patra.identity.domain.port.repository.EmailTokenRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/// EmailVerificationIssuer 单元测试：先作废后保存、只存哈希、链接里带原文。
@DisplayName("EmailVerificationIssuer 单元测试")
class EmailVerificationIssuerTest {

  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
  private static final MailLinkPolicy LINKS =
      MailLinkPolicy.builder()
          .baseUrl("https://patra.example")
          .verifyEmailPath("/verify-email")
          .securitySettingsPath("/settings/security")
          .resetPasswordPath("/reset-password")
          .confirmEmailChangePath("/confirm-email-change")
          .revertEmailChangePath("/revert-email-change")
          .build();

  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final EmailVerificationIssuer issuer =
      new EmailVerificationIssuer(
          tokens, LINKS, Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
  private final User user =
      User.restore()
          .id(42L)
          .email(EmailAddress.of("chen.yu@example.com"))
          .username(Username.of("chen-yu"))
          .status(UserStatus.ACTIVE)
          .version(0L)
          .build();

  @Test
  @DisplayName("先作废旧令牌再保存新的；保存的只有哈希，链接片段里的原文哈希后等于它")
  void should_revoke_active_tokens_then_save_a_hashed_one_and_link_the_secret() {
    when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    PreparedVerificationMail prepared = issuer.prepare(user, "陈宇", "203.0.113.9");

    InOrder order = inOrder(tokens);
    order.verify(tokens).revokeActive(42L, EmailTokenPurpose.EMAIL_VERIFY, NOW);
    ArgumentCaptor<EmailToken> saved = ArgumentCaptor.forClass(EmailToken.class);
    order.verify(tokens).save(saved.capture());
    EmailToken token = saved.getValue();
    assertThat(token.getUserId()).isEqualTo(42L);
    assertThat(token.getPurpose()).isEqualTo(EmailTokenPurpose.EMAIL_VERIFY);
    assertThat(token.getEmail().value()).isEqualTo("chen.yu@example.com");
    assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    assertThat(token.getRequestedIp()).contains("203.0.113.9");
    String url = prepared.mail().verifyUrl();
    assertThat(url).startsWith("https://patra.example/verify-email#token=");
    String secret = url.substring(url.indexOf("#token=") + 7);
    assertThat(new EmailTokenSecret(secret).hash()).isEqualTo(token.getTokenHash());
    assertThat(prepared.mail().displayName()).isEqualTo("陈宇");
    assertThat(prepared.mail().to().value()).isEqualTo("chen.yu@example.com");
    assertThat(prepared.mail().expiresAt()).isEqualTo(token.getExpiresAt());
    assertThat(prepared.token()).isSameAs(token);
    assertThat(prepared.toString()).doesNotContain(secret).doesNotContain("chen.yu");
  }

  @Test
  @DisplayName("每次签发的原文都不同")
  void should_generate_a_different_secret_each_time() {
    when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(issuer.prepare(user, "a", null).mail().verifyUrl())
        .isNotEqualTo(issuer.prepare(user, "a", null).mail().verifyUrl());
  }

  @Test
  @DisplayName("没有邮箱的用户（已注销）不能签发，仓储不被碰")
  void should_fail_for_user_without_email() {
    User deleted =
        User.restore()
            .id(43L)
            .username(Username.of("gone"))
            .status(UserStatus.DELETED)
            .deletedAt(NOW)
            .version(0L)
            .build();

    assertThatThrownBy(() -> issuer.prepare(deleted, "x", null))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(tokens);
  }
}
