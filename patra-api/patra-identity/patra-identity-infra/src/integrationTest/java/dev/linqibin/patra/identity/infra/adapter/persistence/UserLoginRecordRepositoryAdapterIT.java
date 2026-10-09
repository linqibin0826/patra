package dev.linqibin.patra.identity.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.vo.DeviceId;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.linqibin.starter.jpa.autoconfig.HibernatePropertiesCustomizer;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserLoginRecordRepositoryAdapter 集成测试：保存、查询、结束，以及 V2 的检查约束。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
  UserRepositoryAdapter.class,
  UserLoginRecordRepositoryAdapter.class,
  JpaAuditingConfig.class,
  HibernatePropertiesCustomizer.class
})
@ComponentScan(
    basePackages = "dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("UserLoginRecordRepositoryAdapter 集成测试")
class UserLoginRecordRepositoryAdapterIT {

  private static final Instant EXPIRES_AT = Instant.parse("2027-04-07T08:00:00Z");

  @Autowired private UserRepositoryAdapter users;
  @Autowired private UserLoginRecordRepositoryAdapter records;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("保存时分配雪花 ID，能按 ID 查回，登录时间取审计列")
  void should_assign_id_and_find_saved_record() {
    User user = users.save(User.register(EmailAddress.of("login.record@example.com")));

    UserLoginRecord saved =
        records.save(
            UserLoginRecord.start(user.getId(), LoginClient.of("web", "mac-safari"), EXPIRES_AT));

    assertThat(saved.getId()).isPositive();
    assertThat(saved.getVersion()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    UserLoginRecord reloaded = records.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getUserId()).isEqualTo(user.getId());
    assertThat(reloaded.getClientType()).isEqualTo(ClientType.WEB);
    assertThat(reloaded.getDeviceId()).map(DeviceId::value).contains("mac-safari");
    assertThat(reloaded.getExpiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(reloaded.isEnded()).isFalse();
    assertThat(records.findById(1L)).isEmpty();
  }

  @Test
  @DisplayName("结束后保存，查回来有结束时间和原因，版本加一")
  void should_persist_end() {
    User user = users.save(User.register(EmailAddress.of("end.record@example.com")));
    UserLoginRecord saved =
        records.save(UserLoginRecord.start(user.getId(), LoginClient.web(), EXPIRES_AT));
    Instant endedAt = Instant.parse("2026-10-09T09:00:00Z");

    saved.end(LoginEndReason.LOGOUT, endedAt);
    UserLoginRecord ended = records.save(saved);

    UserLoginRecord reloaded = records.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.isEnded()).isTrue();
    assertThat(reloaded.getEndedAt()).isEqualTo(endedAt);
    assertThat(reloaded.getEndReason()).isEqualTo(LoginEndReason.LOGOUT);
    assertThat(reloaded.getDeviceId()).isEmpty();
    assertThat(ended.getVersion()).isEqualTo(saved.getVersion() + 1);
  }

  @Test
  @DisplayName("检查约束：客户端类型只能是 WEB")
  void should_reject_unknown_client_type() {
    User user = users.save(User.register(EmailAddress.of("ck.client@example.com")));

    assertThatThrownBy(() -> insertRaw(user.getId(), "APP", null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_login_record_client_type");
  }

  @Test
  @DisplayName("检查约束：结束原因只能是 LOGOUT、BANNED、REPLACED")
  void should_reject_unknown_end_reason() {
    User user = users.save(User.register(EmailAddress.of("ck.reason@example.com")));

    assertThatThrownBy(() -> insertRaw(user.getId(), "WEB", EXPIRES_AT, "EXPIRED"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_login_record_end_reason");
  }

  @Test
  @DisplayName("检查约束：结束时间和结束原因要么都有要么都没有")
  void should_require_end_fields_together() {
    User user = users.save(User.register(EmailAddress.of("ck.pair@example.com")));

    assertThatThrownBy(() -> insertRaw(user.getId(), "WEB", EXPIRES_AT, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_login_record_ended");
  }

  /// 绕过聚合直接插一行，用来触发检查约束。
  ///
  /// @param userId 用户 ID
  /// @param clientType 客户端类型
  /// @param endedAt 结束时间，可为 `null`
  /// @param endReason 结束原因，可为 `null`
  private void insertRaw(long userId, String clientType, Instant endedAt, String endReason) {
    jdbcTemplate.update(
        "INSERT INTO idn_user_login_record (id, user_id, client_type, expires_at, ended_at, end_reason)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        SnowflakeIdGenerator.getId(),
        userId,
        clientType,
        Timestamp.from(EXPIRES_AT),
        endedAt == null ? null : Timestamp.from(endedAt),
        endReason);
  }
}
