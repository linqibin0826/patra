package dev.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.domain.model.aggregate.EmailToken;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// EmailTokenRepositoryAdapter 集成测试：只存哈希、条件消费只成功一次、按用途 / 按用户作废、分批清理、
/// 部分唯一索引与改邮箱撤销令牌的豁免。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  UserRepositoryAdapter.class,
  EmailTokenRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackages = "dev.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("EmailTokenRepositoryAdapter 集成测试")
class EmailTokenRepositoryAdapterIT {

  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
  private static final SecureRandom RANDOM = new SecureRandom();

  @Autowired private UserRepositoryAdapter users;
  @Autowired private EmailTokenRepositoryAdapter tokens;
  @Autowired private JdbcTemplate jdbcTemplate;

  private long userId;
  private EmailAddress email;

  @BeforeEach
  void saveUser() {
    String address = "token-" + Math.abs(RANDOM.nextLong()) + "@example.com";
    email = EmailAddress.of(address);
    userId =
        users
            .save(
                User.register(email, Username.of("u" + address.substring(6, address.indexOf('@')))))
            .getId();
  }

  /// 条件消费令牌在事务外调用时被拒绝（`MANDATORY`）。
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("条件消费令牌必须在事务里调用")
  void should_reject_lock_outside_transaction() {
    assertThatThrownBy(() -> tokens.consume("hash", EmailTokenPurpose.EMAIL_VERIFY, NOW))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  @DisplayName("库里只有哈希；按哈希能查到；条件消费第一次成功并写消费时间，第二次为空")
  void should_store_only_hash_and_consume_once() {
    EmailTokenSecret secret = EmailTokenSecret.generate(RANDOM);
    EmailToken saved = tokens.save(issued(EmailTokenPurpose.EMAIL_VERIFY, secret, NOW));

    assertThat(saved.getId()).isPositive();
    assertThat(
            jdbcTemplate.queryForObject(
                "select token_hash from idn_email_token where id = ?", String.class, saved.getId()))
        .isEqualTo(secret.hash());
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from idn_email_token where token_hash = ? or email = ?",
                Integer.class,
                secret.value(),
                secret.value()))
        .isZero();
    assertThat(tokens.findByTokenHash(secret.hash()))
        .hasValueSatisfying(
            token -> {
              assertThat(token.getId()).isEqualTo(saved.getId());
              assertThat(token.isActive()).isTrue();
            });
    assertThat(tokens.findByTokenHash("0".repeat(64))).isEmpty();

    Optional<EmailToken> consumed =
        tokens.consume(secret.hash(), EmailTokenPurpose.EMAIL_VERIFY, NOW.plusSeconds(60));

    assertThat(consumed)
        .hasValueSatisfying(
            token -> {
              assertThat(token.getId()).isEqualTo(saved.getId());
              assertThat(token.getUserId()).isEqualTo(userId);
              assertThat(token.getEmail()).isEqualTo(email);
              assertThat(token.getConsumedAt()).isEqualTo(NOW.plusSeconds(60));
              assertThat(token.getRevokedAt()).isNull();
              assertThat(token.getVersion()).isEqualTo(1L);
              assertThat(token.isActive()).isFalse();
            });
    assertThat(tokens.consume(secret.hash(), EmailTokenPurpose.EMAIL_VERIFY, NOW.plusSeconds(61)))
        .isEmpty();
    // 消费绕过了 JPA，同一事务里按哈希再查会拿到一级缓存里的旧实例，所以用 SQL 核对库里的值
    assertThat(
            jdbcTemplate.queryForObject(
                "select consumed_at is not null from idn_email_token where id = ?",
                Boolean.class,
                saved.getId()))
        .isTrue();
  }

  @Test
  @DisplayName("过期当刻、已作废、用途不符都消费不到")
  void should_not_consume_expired_revoked_or_other_purpose_token() {
    EmailTokenSecret expiring = EmailTokenSecret.generate(RANDOM);
    EmailToken saved = tokens.save(issued(EmailTokenPurpose.EMAIL_VERIFY, expiring, NOW));
    assertThat(tokens.consume(expiring.hash(), EmailTokenPurpose.PASSWORD_RESET, NOW)).isEmpty();
    assertThat(
            tokens.consume(expiring.hash(), EmailTokenPurpose.EMAIL_VERIFY, saved.getExpiresAt()))
        .isEmpty();
    assertThat(
            tokens.consume(
                expiring.hash(),
                EmailTokenPurpose.EMAIL_VERIFY,
                saved.getExpiresAt().minusSeconds(1)))
        .isPresent();

    EmailTokenSecret revoked = EmailTokenSecret.generate(RANDOM);
    tokens.save(issued(EmailTokenPurpose.PASSWORD_RESET, revoked, NOW));
    assertThat(tokens.revokeActive(userId, EmailTokenPurpose.PASSWORD_RESET, NOW.plusSeconds(1)))
        .isEqualTo(1);
    assertThat(tokens.consume(revoked.hash(), EmailTokenPurpose.PASSWORD_RESET, NOW.plusSeconds(2)))
        .isEmpty();
  }

  @Test
  @DisplayName("按用途作废只动该用途的有效行：已消费的不动，别的用途不动")
  void should_revoke_only_active_tokens_of_the_purpose() {
    EmailTokenSecret first = EmailTokenSecret.generate(RANDOM);
    EmailToken consumed = tokens.save(issued(EmailTokenPurpose.EMAIL_VERIFY, first, NOW));
    tokens.consume(first.hash(), EmailTokenPurpose.EMAIL_VERIFY, NOW.plusSeconds(1));
    EmailToken second =
        tokens.save(issued(EmailTokenPurpose.EMAIL_VERIFY, EmailTokenSecret.generate(RANDOM), NOW));
    EmailToken other =
        tokens.save(
            issued(EmailTokenPurpose.PASSWORD_RESET, EmailTokenSecret.generate(RANDOM), NOW));

    assertThat(tokens.revokeActive(userId, EmailTokenPurpose.EMAIL_VERIFY, NOW.plusSeconds(5)))
        .isEqualTo(1);

    assertThat(revokedAt(consumed.getId())).isNull();
    assertThat(revokedAt(second.getId())).isNotNull();
    assertThat(revokedAt(other.getId())).isNull();
  }

  @Test
  @DisplayName("按用户作废动全部用途的有效行，别的用户不受影响")
  void should_revoke_all_active_tokens_of_user() {
    EmailToken verify =
        tokens.save(issued(EmailTokenPurpose.EMAIL_VERIFY, EmailTokenSecret.generate(RANDOM), NOW));
    EmailToken reset =
        tokens.save(
            issued(EmailTokenPurpose.PASSWORD_RESET, EmailTokenSecret.generate(RANDOM), NOW));
    EmailAddress otherEmail = EmailAddress.of("other-" + userId + "@example.com");
    long otherUser =
        users.save(User.register(otherEmail, Username.of("u-other-" + userId))).getId();
    EmailToken othersToken =
        tokens.save(
            EmailToken.issue(
                EmailTokenPurpose.EMAIL_VERIFY,
                otherUser,
                otherEmail,
                EmailTokenSecret.generate(RANDOM),
                NOW,
                null));

    assertThat(tokens.revokeAllActive(userId, NOW.plusSeconds(5))).isEqualTo(2);

    assertThat(revokedAt(verify.getId())).isNotNull();
    assertThat(revokedAt(reset.getId())).isNotNull();
    assertThat(revokedAt(othersToken.getId())).isNull();
  }

  @Test
  @DisplayName("按用户删行：删掉该用户全部令牌（含撤销令牌），别的用户不受影响")
  void should_delete_all_tokens_of_one_user_only() {
    tokens.save(issued(EmailTokenPurpose.EMAIL_VERIFY, EmailTokenSecret.generate(RANDOM), NOW));
    tokens.save(issued(EmailTokenPurpose.PASSWORD_RESET, EmailTokenSecret.generate(RANDOM), NOW));
    tokens.save(
        issued(EmailTokenPurpose.EMAIL_CHANGE_REVERT, EmailTokenSecret.generate(RANDOM), NOW));
    EmailAddress otherEmail = EmailAddress.of("other-del-" + userId + "@example.com");
    long otherUser = users.save(User.register(otherEmail, Username.of("u-od-" + userId))).getId();
    tokens.save(
        EmailToken.issue(
            EmailTokenPurpose.EMAIL_VERIFY,
            otherUser,
            otherEmail,
            EmailTokenSecret.generate(RANDOM),
            NOW,
            null));

    assertThat(tokens.deleteAllByUserId(userId)).isEqualTo(3);

    assertThat(countTokens(userId)).isZero();
    assertThat(countTokens(otherUser)).isEqualTo(1);
  }

  /// 数某个用户的令牌行。
  ///
  /// @param user 用户 ID
  /// @return 行数
  private int countTokens(long user) {
    return jdbcTemplate.queryForObject(
        "select count(*) from idn_email_token where user_id = ?", Integer.class, user);
  }

  @Test
  @DisplayName("改邮箱的撤销令牌可以并存，按用户作废时留着；按用途作废才清掉")
  void should_let_email_change_revert_tokens_coexist_and_survive_revoke_all() {
    EmailToken first =
        tokens.save(
            issued(EmailTokenPurpose.EMAIL_CHANGE_REVERT, EmailTokenSecret.generate(RANDOM), NOW));
    EmailToken second =
        tokens.save(
            issued(EmailTokenPurpose.EMAIL_CHANGE_REVERT, EmailTokenSecret.generate(RANDOM), NOW));

    assertThat(tokens.revokeAllActive(userId, NOW.plusSeconds(5))).isZero();
    assertThat(revokedAt(first.getId())).isNull();
    assertThat(revokedAt(second.getId())).isNull();
    assertThat(
            tokens.revokeActive(userId, EmailTokenPurpose.EMAIL_CHANGE_REVERT, NOW.plusSeconds(6)))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("过期早于截止时间的行按批删，截止时间之内和未过期的留着")
  void should_delete_expired_tokens_in_batches() {
    Instant longAgo = NOW.minus(Duration.ofDays(40));
    for (EmailTokenPurpose purpose :
        new EmailTokenPurpose[] {
          EmailTokenPurpose.EMAIL_VERIFY,
          EmailTokenPurpose.PASSWORD_RESET,
          EmailTokenPurpose.EMAIL_CHANGE_CONFIRM
        }) {
      tokens.save(issued(purpose, EmailTokenSecret.generate(RANDOM), longAgo));
    }
    tokens.save(
        issued(
            EmailTokenPurpose.EMAIL_CHANGE_REVERT,
            EmailTokenSecret.generate(RANDOM),
            NOW.minus(Duration.ofDays(20))));
    // 过期但没作废的 EMAIL_VERIFY 仍占着「只留一个有效令牌」的位置，签发新的前先作废（和签发流程一致）
    tokens.revokeActive(userId, EmailTokenPurpose.EMAIL_VERIFY, NOW);
    tokens.save(issued(EmailTokenPurpose.EMAIL_VERIFY, EmailTokenSecret.generate(RANDOM), NOW));
    Instant cutoff = NOW.minus(Duration.ofDays(30));

    assertThat(tokens.deleteExpiredBefore(cutoff, 2)).isEqualTo(2);
    assertThat(tokens.deleteExpiredBefore(cutoff, 2)).isEqualTo(1);
    assertThat(tokens.deleteExpiredBefore(cutoff, 2)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from idn_email_token where user_id = ?", Integer.class, userId))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("同一用户同一用途不作废就存第二条有效令牌：撞部分唯一索引，转成并发修改异常")
  void should_reject_second_active_token_for_same_user_and_purpose() {
    tokens.save(issued(EmailTokenPurpose.EMAIL_VERIFY, EmailTokenSecret.generate(RANDOM), NOW));

    assertThatThrownBy(
            () ->
                tokens.save(
                    issued(EmailTokenPurpose.EMAIL_VERIFY, EmailTokenSecret.generate(RANDOM), NOW)))
        .isInstanceOf(UserModifiedConcurrentlyException.class);
  }

  @Test
  @DisplayName("查有效令牌：只返回未消费、未作废、未过期的那条；别的用途查不到")
  void should_find_active_token_only_when_unused_unrevoked_and_unexpired() {
    EmailToken consumed =
        tokens.save(
            issued(EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, EmailTokenSecret.generate(RANDOM), NOW));
    tokens.consume(
        consumed.getTokenHash(), EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW.plusSeconds(1));
    EmailToken active =
        tokens.save(
            issued(EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, EmailTokenSecret.generate(RANDOM), NOW));

    assertThat(
            tokens.findActive(userId, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW.plusSeconds(2)))
        .map(EmailToken::getId)
        .contains(active.getId());
    assertThat(
            tokens.findActive(
                userId, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, active.getExpiresAt()))
        .isEmpty();
    assertThat(tokens.findActive(userId, EmailTokenPurpose.PASSWORD_RESET, NOW.plusSeconds(2)))
        .isEmpty();
    assertThat(
            tokens.revokeActive(userId, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW.plusSeconds(3)))
        .isEqualTo(1);
    assertThat(
            tokens.findActive(userId, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW.plusSeconds(4)))
        .isEmpty();
  }

  @Test
  @DisplayName("按签发先后作废撤销令牌：只作废比指定那条更晚签发的，更早的保留")
  void should_revoke_only_revert_tokens_issued_after_the_given_one() {
    EmailToken first =
        tokens.save(
            issued(EmailTokenPurpose.EMAIL_CHANGE_REVERT, EmailTokenSecret.generate(RANDOM), NOW));
    EmailToken second =
        tokens.save(
            issued(EmailTokenPurpose.EMAIL_CHANGE_REVERT, EmailTokenSecret.generate(RANDOM), NOW));
    EmailToken third =
        tokens.save(
            issued(EmailTokenPurpose.EMAIL_CHANGE_REVERT, EmailTokenSecret.generate(RANDOM), NOW));

    assertThat(tokens.revokeRevertTokensIssuedAfter(userId, second.getId(), NOW.plusSeconds(1)))
        .isEqualTo(1);

    assertThat(revokedAt(first.getId())).isNull();
    assertThat(revokedAt(second.getId())).isNull();
    assertThat(revokedAt(third.getId())).isNotNull();
  }

  /// 给当前用户签发一枚令牌，不保存。
  ///
  /// @param purpose 用途
  /// @param secret 原文
  /// @param now 签发时间
  /// @return 令牌
  private EmailToken issued(EmailTokenPurpose purpose, EmailTokenSecret secret, Instant now) {
    return EmailToken.issue(purpose, userId, email, secret, now, "203.0.113.9");
  }

  /// 读某行的作废时间。
  ///
  /// @param id 令牌 ID
  /// @return 作废时间；没作废时为 `null`
  private Object revokedAt(long id) {
    return jdbcTemplate.queryForObject(
        "select revoked_at from idn_email_token where id = ?", Object.class, id);
  }
}
