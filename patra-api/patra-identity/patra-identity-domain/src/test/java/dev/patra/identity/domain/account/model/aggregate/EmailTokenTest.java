package dev.patra.identity.domain.account.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.aggregate.EmailToken.EmailTokenBuilder;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// EmailToken 单元测试：签发、可用性判断、消费与作废、从库里恢复。
@DisplayName("EmailToken 单元测试")
class EmailTokenTest {

  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final EmailTokenSecret SECRET = new EmailTokenSecret("B".repeat(43));

  @Test
  @DisplayName("签发：过期时间取自用途，只存哈希，toString 不带哈希和邮箱")
  void should_issue_with_expiry_from_purpose_and_store_only_hash() {
    EmailToken token =
        EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, SECRET, NOW, "203.0.113.9");

    assertThat(token.getId()).isNull();
    assertThat(token.getUserId()).isEqualTo(42L);
    assertThat(token.getPurpose()).isEqualTo(EmailTokenPurpose.EMAIL_VERIFY);
    assertThat(token.getEmail()).isEqualTo(EMAIL);
    assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    assertThat(token.getTokenHash()).isEqualTo(SECRET.hash());
    assertThat(token.getRequestedIp()).contains("203.0.113.9");
    assertThat(token.isActive()).isTrue();
    assertThat(token.isUsableAt(NOW)).isTrue();
    assertThat(token.isUsableAt(token.getExpiresAt())).isFalse();
    assertThat(token.toString())
        .contains("userId=42", "purpose=EMAIL_VERIFY")
        .doesNotContain(SECRET.hash())
        .doesNotContain("chen.yu");
  }

  @ParameterizedTest
  @EnumSource(EmailTokenPurpose.class)
  @DisplayName("签发：每种用途都按自己的有效期算过期时间")
  void should_expire_after_the_validity_of_each_purpose(EmailTokenPurpose purpose) {
    EmailToken token = EmailToken.issue(purpose, 42L, EMAIL, SECRET, NOW, null);

    assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(purpose.validity()));
  }

  @Test
  @DisplayName("到过期那一刻就不可用，前一纳秒还可用")
  void should_be_usable_until_the_instant_it_expires() {
    EmailToken token =
        EmailToken.issue(EmailTokenPurpose.PASSWORD_RESET, 42L, EMAIL, SECRET, NOW, null);
    Instant expiresAt = token.getExpiresAt();

    assertThat(token.isUsableAt(expiresAt.minusNanos(1))).isTrue();
    assertThat(token.isUsableAt(expiresAt)).isFalse();
    assertThat(token.isUsableAt(expiresAt.plusSeconds(1))).isFalse();
  }

  @Test
  @DisplayName("消费或作废之后不可用，再结算一次抛 IllegalStateException")
  void should_not_be_usable_once_consumed_or_revoked_and_reject_second_settlement() {
    EmailToken consumed =
        EmailToken.issue(EmailTokenPurpose.PASSWORD_RESET, 42L, EMAIL, SECRET, NOW, null);
    consumed.markConsumed(NOW.plusSeconds(1));
    assertThat(consumed.isUsableAt(NOW.plusSeconds(2))).isFalse();
    assertThat(consumed.getRequestedIp()).isEmpty();
    assertThatThrownBy(() -> consumed.markRevoked(NOW.plusSeconds(3)))
        .isInstanceOf(IllegalStateException.class);
    EmailToken revoked =
        EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, SECRET, NOW, null);
    revoked.markRevoked(NOW);
    assertThat(revoked.isActive()).isFalse();
  }

  @Test
  @DisplayName("消费和作废各记各的时间；已结算之后无论再消费还是再作废，都保留第一次的结果")
  void should_record_settlement_time_and_keep_the_first_settlement() {
    EmailToken consumed =
        EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, SECRET, NOW, null);
    consumed.markConsumed(NOW.plusSeconds(1));
    assertThatThrownBy(() -> consumed.markConsumed(NOW.plusSeconds(2)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> consumed.markRevoked(NOW.plusSeconds(2)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(consumed.getConsumedAt()).isEqualTo(NOW.plusSeconds(1));
    assertThat(consumed.getRevokedAt()).isNull();

    EmailToken revoked =
        EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, SECRET, NOW, null);
    revoked.markRevoked(NOW.plusSeconds(3));
    assertThatThrownBy(() -> revoked.markRevoked(NOW.plusSeconds(4)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> revoked.markConsumed(NOW.plusSeconds(4)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(revoked.getRevokedAt()).isEqualTo(NOW.plusSeconds(3));
    assertThat(revoked.getConsumedAt()).isNull();
  }

  @Test
  @DisplayName("从构建器恢复")
  void should_restore_from_builder() {
    EmailToken token =
        EmailToken.restore()
            .id(1L)
            .userId(42L)
            .purpose(EmailTokenPurpose.EMAIL_CHANGE_REVERT)
            .email(EMAIL)
            .tokenHash(SECRET.hash())
            .expiresAt(NOW.plus(Duration.ofDays(7)))
            .version(0L)
            .build();

    assertThat(token.isUsableAt(NOW.plus(Duration.ofDays(6)))).isTrue();
  }

  @Test
  @DisplayName("恢复：每一列原样带回；有消费时间或作废时间的令牌不再有效")
  void should_restore_every_column_and_settled_tokens_are_inactive() {
    Instant settledAt = NOW.plusSeconds(60);
    EmailToken consumed =
        restorable()
            .consumedAt(settledAt)
            .requestedIp("203.0.113.9")
            .version(3L)
            .createdAt(NOW)
            .updatedAt(settledAt)
            .build();

    assertThat(consumed.getId()).isEqualTo(1L);
    assertThat(consumed.getUserId()).isEqualTo(42L);
    assertThat(consumed.getPurpose()).isEqualTo(EmailTokenPurpose.PASSWORD_RESET);
    assertThat(consumed.getEmail()).isEqualTo(EMAIL);
    assertThat(consumed.getTokenHash()).isEqualTo(SECRET.hash());
    assertThat(consumed.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    assertThat(consumed.getConsumedAt()).isEqualTo(settledAt);
    assertThat(consumed.getRevokedAt()).isNull();
    assertThat(consumed.getRequestedIp()).contains("203.0.113.9");
    assertThat(consumed.getVersion()).isEqualTo(3L);
    assertThat(consumed.getCreatedAt()).isEqualTo(NOW);
    assertThat(consumed.getUpdatedAt()).isEqualTo(settledAt);
    assertThat(consumed.isActive()).isFalse();
    assertThat(consumed.isUsableAt(NOW)).isFalse();

    EmailToken revoked = restorable().revokedAt(settledAt).build();
    assertThat(revoked.getRevokedAt()).isEqualTo(settledAt);
    assertThat(revoked.getRequestedIp()).isEmpty();
    assertThat(revoked.isActive()).isFalse();
    assertThat(revoked.isUsableAt(NOW)).isFalse();
  }

  @Test
  @DisplayName("恢复时 id、userId、purpose、email、tokenHash、expiresAt 不能为 null")
  void should_require_core_fields_on_restore() {
    assertThatThrownBy(() -> restorable().id(null).build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> restorable().userId(null).build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> restorable().purpose(null).build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> restorable().email(null).build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> restorable().tokenHash(null).build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> restorable().expiresAt(null).build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("签发和结算的必填参数不能为 null")
  void should_reject_null_arguments() {
    EmailToken token =
        EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, SECRET, NOW, null);

    assertThatThrownBy(() -> EmailToken.issue(null, 42L, EMAIL, SECRET, NOW, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, null, SECRET, NOW, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, null, NOW, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> EmailToken.issue(EmailTokenPurpose.EMAIL_VERIFY, 42L, EMAIL, SECRET, null, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> token.isUsableAt(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> token.markConsumed(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> token.markRevoked(null)).isInstanceOf(NullPointerException.class);
    assertThat(token.isActive()).isTrue();
  }

  /// 只填了必填列的恢复构建器，各个测试在它上面补可选列或把某一列置空。
  ///
  /// @return 恢复构建器
  private static EmailTokenBuilder restorable() {
    return EmailToken.restore()
        .id(1L)
        .userId(42L)
        .purpose(EmailTokenPurpose.PASSWORD_RESET)
        .email(EMAIL)
        .tokenHash(SECRET.hash())
        .expiresAt(NOW.plus(Duration.ofHours(1)));
  }
}
