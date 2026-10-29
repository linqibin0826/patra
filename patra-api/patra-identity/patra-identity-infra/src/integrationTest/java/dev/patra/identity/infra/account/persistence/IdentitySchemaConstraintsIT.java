package dev.patra.identity.infra.account.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import java.sql.Timestamp;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 建表脚本里的检查约束、唯一约束和外键：绕过应用层直接写库，数据库自己也要拦住不合法的数据。
/// 每个用例只有一条会失败的写入：测试事务在第一次失败后就被 PostgreSQL 标成 aborted，后面的语句一律报错。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("identity 表约束")
class IdentitySchemaConstraintsIT {

  private static final String INSERT_USER =
      "INSERT INTO idn_user (id, email, username, status, banned_at, deletion_due_at, "
          + "deleted_at, email_verified_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String INSERT_PROFILE =
      "INSERT INTO idn_user_profile (id, user_id, display_name) VALUES (?, ?, ?)";

  private static final String INSERT_FORMER =
      "INSERT INTO idn_user_former_username (id, user_id, username, replaced_at, reserved_until) "
          + "VALUES (?, ?, ?, ?, ?)";

  private static final Timestamp AT = Timestamp.valueOf("2026-10-20 08:00:00");

  private static final String INSERT_TOKEN =
      "INSERT INTO idn_email_token (id, user_id, purpose, email, token_hash, expires_at, "
          + "consumed_at, revoked_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String HASH = "a".repeat(64);

  @Autowired private JdbcTemplate jdbcTemplate;

  static Stream<String> malformedUsernames() {
    return Stream.of("Chen", "a--b", "a__b", "-ab", "ab-", "123456", "ab", "chen.yu");
  }

  @ParameterizedTest
  @MethodSource("malformedUsernames")
  @DisplayName("用户名的格式由数据库再守一遍：大写、连续符号、首尾符号、纯数字、太短、字符")
  void should_reject_malformed_username(String username) {
    assertThatThrownBy(() -> insertActive(1L, "a@example.com", username))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_username_format");
  }

  @Test
  @DisplayName("超过 30 个字符的用户名被列长度拦住")
  void should_reject_username_over_30_chars() {
    assertThatThrownBy(() -> insertActive(1L, "a@example.com", "a".repeat(31)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("character varying(30)");
  }

  @Test
  @DisplayName("用户名唯一")
  void should_reject_duplicate_username() {
    insertActive(2L, "first@example.com", "same-name");
    assertThatThrownBy(() -> insertActive(3L, "second@example.com", "same-name"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_idn_user_username");
  }

  @Test
  @DisplayName("邮箱必须是小写")
  void should_reject_uppercase_email() {
    assertThatThrownBy(() -> insertActive(4L, "Upper@Example.com", "upper"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_email_lowercase");
  }

  @Test
  @DisplayName("状态只能是四态之一")
  void should_reject_unknown_status() {
    assertThatThrownBy(
            () -> insert(5L, "a@example.com", "frozen", "FROZEN", null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_status");
  }

  @Test
  @DisplayName("封禁状态必须有封禁时间")
  void should_require_banned_at_when_banned() {
    assertThatThrownBy(
            () -> insert(6L, "b@example.com", "banned", "BANNED", null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_banned_at");
  }

  @Test
  @DisplayName("正常状态不能有封禁时间")
  void should_reject_banned_at_when_active() {
    assertThatThrownBy(() -> insert(7L, "c@example.com", "active", "ACTIVE", AT, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_banned_at");
  }

  @Test
  @DisplayName("冷静期必须有到期时间")
  void should_require_deletion_due_at_when_pending() {
    assertThatThrownBy(
            () ->
                insert(8L, "d@example.com", "pending", "PENDING_DELETION", null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_deletion_due_at");
  }

  @Test
  @DisplayName("不在冷静期不能有到期时间")
  void should_reject_deletion_due_at_otherwise() {
    assertThatThrownBy(() -> insert(9L, "e@example.com", "active2", "ACTIVE", null, AT, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_deletion_due_at");
  }

  @Test
  @DisplayName("已注销必须有完成时间")
  void should_require_deleted_at_when_deleted() {
    assertThatThrownBy(() -> insert(10L, null, "deleted-1", "DELETED", null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_deleted_at");
  }

  @Test
  @DisplayName("未注销不能有完成时间")
  void should_reject_deleted_at_otherwise() {
    assertThatThrownBy(
            () -> insert(11L, "f@example.com", "active3", "ACTIVE", null, null, AT, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_deleted_at");
  }

  @Test
  @DisplayName("已注销的用户不能有邮箱")
  void should_require_null_email_when_deleted() {
    assertThatThrownBy(
            () -> insert(12L, "g@example.com", "deleted-2", "DELETED", null, null, AT, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_email_deleted");
  }

  @Test
  @DisplayName("未注销的用户必须有邮箱")
  void should_reject_null_email_when_active() {
    assertThatThrownBy(() -> insert(13L, null, "active4", "ACTIVE", null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_email_deleted");
  }

  @Test
  @DisplayName("没有邮箱就不能有邮箱验证时间")
  void should_reject_verified_at_without_email() {
    assertThatThrownBy(() -> insert(14L, null, "deleted-3", "DELETED", null, null, AT, AT))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_email_verified_at");
  }

  @Test
  @DisplayName("多个已注销的用户都没有邮箱，唯一约束不冲突")
  void should_accept_two_deleted_users_without_email() {
    assertThatCode(
            () -> {
              insert(15L, null, "deleted-4", "DELETED", null, null, AT, null);
              insert(16L, null, "deleted-5", "DELETED", null, null, AT, null);
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("凭据必须指向存在的用户")
  void should_reject_credential_of_missing_user() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO idn_user_password_credential (id, user_id, password_hash) "
                        + "VALUES (?, ?, ?)",
                    17L,
                    999_999L,
                    "$argon2id$x"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_idn_user_password_credential_user");
  }

  @Test
  @DisplayName("资料的昵称最多 120 字节")
  void should_reject_profile_display_name_over_120_bytes() {
    insertActive(20L, "profile-long@example.com", "profile-long");
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_PROFILE, 21L, 20L, "汉".repeat(41)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_profile_display_name");
  }

  @Test
  @DisplayName("资料的昵称可以为空：空表示跟随用户名")
  void should_accept_profile_without_display_name() {
    insertActive(22L, "profile-null@example.com", "profile-null");
    assertThatCode(() -> jdbcTemplate.update(INSERT_PROFILE, 23L, 22L, null))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("资料必须指向存在的用户")
  void should_reject_profile_of_missing_user() {
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_PROFILE, 24L, 999_998L, "陈宇"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_idn_user_profile_user");
  }

  @Test
  @DisplayName("旧名的保留到期时间必须晚于改名时间")
  void should_reject_former_username_with_reservation_not_after_replacement() {
    insertActive(30L, "former-period@example.com", "former-period");
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_FORMER, 31L, 30L, "old-name", AT, AT))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_former_username_period");
  }

  @Test
  @DisplayName("旧名也要符合用户名的格式")
  void should_reject_former_username_with_bad_format() {
    insertActive(32L, "former-format@example.com", "former-format");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    INSERT_FORMER,
                    33L,
                    32L,
                    "Old-Name",
                    AT,
                    Timestamp.valueOf("2027-01-18 08:00:00")))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_former_username_format");
  }

  @Test
  @DisplayName("令牌用途只能是四种之一")
  void should_reject_unknown_token_purpose() {
    insertActive(40L, "token-purpose@example.com", "token-purpose");
    assertThatThrownBy(() -> insertToken(41L, 40L, "MAGIC_LINK", "token-purpose@example.com", HASH))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_email_token_purpose");
  }

  @Test
  @DisplayName("令牌发往的邮箱必须是小写")
  void should_reject_uppercase_token_email() {
    insertActive(42L, "token-email@example.com", "token-email");
    assertThatThrownBy(() -> insertToken(43L, 42L, "EMAIL_VERIFY", "Token-Email@example.com", HASH))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_email_token_email_lowercase");
  }

  @Test
  @DisplayName("令牌哈希必须是 64 位小写十六进制")
  void should_reject_malformed_token_hash() {
    insertActive(44L, "token-hash@example.com", "token-hash");
    assertThatThrownBy(
            () -> insertToken(45L, 44L, "EMAIL_VERIFY", "token-hash@example.com", "A".repeat(64)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_email_token_hash_format");
  }

  @Test
  @DisplayName("令牌不能同时有消费时间和作废时间")
  void should_reject_token_settled_twice() {
    insertActive(46L, "token-settled@example.com", "token-settled");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    INSERT_TOKEN,
                    47L,
                    46L,
                    "EMAIL_VERIFY",
                    "token-settled@example.com",
                    HASH,
                    Timestamp.valueOf("2026-10-21 08:00:00"),
                    AT,
                    AT))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_email_token_settled_once");
  }

  @Test
  @DisplayName("令牌哈希唯一")
  void should_reject_duplicate_token_hash() {
    insertActive(48L, "token-dup@example.com", "token-dup");
    insertToken(49L, 48L, "EMAIL_VERIFY", "token-dup@example.com", HASH);
    assertThatThrownBy(() -> insertToken(50L, 48L, "PASSWORD_RESET", "token-dup@example.com", HASH))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_idn_email_token_hash");
  }

  @Test
  @DisplayName("令牌必须指向存在的用户")
  void should_reject_token_of_missing_user() {
    assertThatThrownBy(() -> insertToken(51L, 999_997L, "EMAIL_VERIFY", "x@example.com", HASH))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_idn_email_token_user");
  }

  @Test
  @DisplayName("登录记录的结束原因可以是 REVOKED")
  void should_accept_revoked_login_end_reason() {
    insertActive(52L, "revoked@example.com", "revoked-user");
    assertThatCode(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO idn_user_login_record (id, user_id, client_type, device_class, "
                        + "expires_at, ended_at, end_reason) VALUES (?, ?, 'WEB', 'UNKNOWN', ?, ?,"
                        + " 'REVOKED')",
                    53L,
                    52L,
                    Timestamp.valueOf("2027-04-18 08:00:00"),
                    AT))
        .doesNotThrowAnyException();
  }

  /// 插一枚没消费、没作废、明天过期的令牌。
  ///
  /// @param id 令牌 ID
  /// @param userId 用户 ID
  /// @param purpose 用途
  /// @param email 发往的邮箱
  /// @param hash 令牌哈希
  private void insertToken(long id, long userId, String purpose, String email, String hash) {
    jdbcTemplate.update(
        INSERT_TOKEN,
        id,
        userId,
        purpose,
        email,
        hash,
        Timestamp.valueOf("2026-10-21 08:00:00"),
        null,
        null);
  }

  /// 插一个正常状态的用户。
  ///
  /// @param id 用户 ID
  /// @param email 邮箱
  /// @param username 用户名
  private void insertActive(long id, String email, String username) {
    insert(id, email, username, "ACTIVE", null, null, null, null);
  }

  /// 按列顺序插一个用户。
  ///
  /// @param id 用户 ID
  /// @param email 邮箱，可以为 `null`
  /// @param username 用户名
  /// @param status 状态
  /// @param bannedAt 封禁时间
  /// @param deletionDueAt 注销到期时间
  /// @param deletedAt 注销完成时间
  /// @param emailVerifiedAt 邮箱验证时间
  private void insert(
      long id,
      String email,
      String username,
      String status,
      Timestamp bannedAt,
      Timestamp deletionDueAt,
      Timestamp deletedAt,
      Timestamp emailVerifiedAt) {
    jdbcTemplate.update(
        INSERT_USER,
        id,
        email,
        username,
        status,
        bannedAt,
        deletionDueAt,
        deletedAt,
        emailVerifiedAt);
  }
}
