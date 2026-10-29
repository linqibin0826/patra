package dev.patra.identity.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/// EmailTokenIssuer 单元测试：先作废同用途再保存、只存哈希、撤销令牌不作废旧的。
@DisplayName("EmailTokenIssuer 单元测试")
class EmailTokenIssuerTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");

  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final EmailTokenIssuer issuer =
      new EmailTokenIssuer(tokens, Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());

  @Test
  @DisplayName("先作废同用途的旧令牌再保存新的；保存的只有哈希，原文的哈希等于它；toString 不含原文")
  void should_revoke_same_purpose_then_save_hashed_token() {
    when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    IssuedEmailToken issued =
        issuer.issue(EmailTokenPurpose.PASSWORD_RESET, 42L, EMAIL, "203.0.113.9");

    InOrder order = inOrder(tokens);
    order.verify(tokens).revokeActive(42L, EmailTokenPurpose.PASSWORD_RESET, NOW);
    ArgumentCaptor<EmailToken> saved = ArgumentCaptor.forClass(EmailToken.class);
    order.verify(tokens).save(saved.capture());
    EmailToken token = saved.getValue();
    assertThat(issued.token()).isSameAs(token);
    assertThat(token.getUserId()).isEqualTo(42L);
    assertThat(token.getPurpose()).isEqualTo(EmailTokenPurpose.PASSWORD_RESET);
    assertThat(token.getEmail()).isEqualTo(EMAIL);
    assertThat(token.getTokenHash()).isEqualTo(issued.secret().hash());
    assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    assertThat(token.getRequestedIp()).contains("203.0.113.9");
    assertThat(issued.toString()).doesNotContain(issued.secret().value());
  }

  @Test
  @DisplayName("撤销令牌可并存：签发时不作废已有的撤销令牌")
  void should_not_revoke_existing_revert_tokens() {
    when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    issuer.issue(EmailTokenPurpose.EMAIL_CHANGE_REVERT, 42L, EMAIL, null);

    verify(tokens, never()).revokeActive(anyLong(), any(), any());
    verify(tokens).save(any());
  }

  @Test
  @DisplayName("每次签发的原文都不同")
  void should_generate_a_different_secret_each_time() {
    when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(issuer.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, null).secret())
        .isNotEqualTo(issuer.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, null).secret());
  }
}
