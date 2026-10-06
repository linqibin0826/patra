package dev.linqibin.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import java.sql.Timestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 建表脚本里的检查约束和外键：绕过应用层直接写库，数据库自己也要拦住不合法的数据。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("identity 表约束")
class IdentitySchemaConstraintsIT {

  private static final String INSERT_USER =
      "INSERT INTO idn_user (id, email, status, banned_at) VALUES (?, ?, ?, ?)";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("邮箱必须是小写")
  void should_reject_uppercase_email() {
    assertThatThrownBy(
            () -> jdbcTemplate.update(INSERT_USER, 1L, "Upper@Example.com", "ACTIVE", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_email_lowercase");
  }

  @Test
  @DisplayName("状态只能是 ACTIVE 或 BANNED")
  void should_reject_unknown_status() {
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_USER, 2L, "a@example.com", "DELETED", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_status");
  }

  @Test
  @DisplayName("封禁状态必须有封禁时间")
  void should_require_banned_at_when_banned() {
    assertThatThrownBy(() -> jdbcTemplate.update(INSERT_USER, 3L, "b@example.com", "BANNED", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_banned_at");
  }

  @Test
  @DisplayName("正常状态不能有封禁时间")
  void should_reject_banned_at_when_active() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    INSERT_USER,
                    4L,
                    "c@example.com",
                    "ACTIVE",
                    Timestamp.valueOf("2026-10-06 08:00:00")))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_banned_at");
  }

  @Test
  @DisplayName("凭据必须指向存在的用户")
  void should_reject_credential_of_missing_user() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO idn_user_password_credential (id, user_id, password_hash) "
                        + "VALUES (?, ?, ?)",
                    5L,
                    999_999L,
                    "$argon2id$x"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_idn_user_password_credential_user");
  }
}
