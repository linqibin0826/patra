package dev.patra.identity.infra.session.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.session.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.infra.account.persistence.UserRepositoryAdapter;
import dev.patra.identity.infra.account.persistence.mapper.UserJpaMapper;
import dev.patra.identity.infra.config.IdentityITPostgreSQLContainerInitializer;
import dev.patra.identity.infra.session.persistence.mapper.UserLoginRecordJpaMapper;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// UserLoginRecordRepositoryAdapter 集成测试：保存、查询、结束，以及 V2 的检查约束。
@DataJpaTest
@ContextConfiguration(initializers = IdentityITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration({FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({
  UserRepositoryAdapter.class,
  UserLoginRecordRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class
})
@ComponentScan(basePackageClasses = {UserJpaMapper.class, UserLoginRecordJpaMapper.class})
@ActiveProfiles("test")
@DisplayName("UserLoginRecordRepositoryAdapter 集成测试")
class UserLoginRecordRepositoryAdapterIT {

  private static final Instant EXPIRES_AT = Instant.parse("2027-04-07T08:00:00Z");
  private static final String DEVICE = "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy";
  private static final ClientOrigin UNKNOWN_ORIGIN =
      ClientOrigin.builder().deviceClass(DeviceClass.UNKNOWN).build();

  @Autowired private UserRepositoryAdapter users;
  @Autowired private UserLoginRecordRepositoryAdapter records;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("保存时分配雪花 ID，能按 ID 查回，登录时间取审计列")
  void should_assign_id_and_find_saved_record() {
    User user =
        users.save(
            User.register(
                EmailAddress.of("login.record@example.com"), Username.of("u-login-record")));

    UserLoginRecord saved =
        records.save(
            UserLoginRecord.start(
                user.getId(), LoginClient.of("web", DEVICE), UNKNOWN_ORIGIN, EXPIRES_AT));

    assertThat(saved.getId()).isPositive();
    assertThat(saved.getVersion()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    UserLoginRecord reloaded = records.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getUserId()).isEqualTo(user.getId());
    assertThat(reloaded.getClientType()).isEqualTo(ClientType.WEB);
    assertThat(reloaded.getDeviceId()).map(DeviceId::value).contains(DEVICE);
    assertThat(reloaded.getExpiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(reloaded.isEnded()).isFalse();
    assertThat(records.findById(1L)).isEmpty();
  }

  @Test
  @DisplayName("结束后保存，查回来有结束时间和原因，版本加一")
  void should_persist_end() {
    User user =
        users.save(
            User.register(EmailAddress.of("end.record@example.com"), Username.of("u-end-record")));
    UserLoginRecord saved =
        records.save(
            UserLoginRecord.start(user.getId(), LoginClient.web(), UNKNOWN_ORIGIN, EXPIRES_AT));
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
    User user =
        users.save(
            User.register(EmailAddress.of("ck.client@example.com"), Username.of("u-ck-client")));

    assertThatThrownBy(() -> insertRaw(user.getId(), "APP", null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_login_record_client_type");
  }

  @Test
  @DisplayName("检查约束：结束原因只能是第 9.2 节的八个取值")
  void should_reject_unknown_end_reason() {
    User user =
        users.save(
            User.register(EmailAddress.of("ck.reason@example.com"), Username.of("u-ck-reason")));

    assertThatThrownBy(() -> insertRaw(user.getId(), "WEB", EXPIRES_AT, "EXPIRED"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_login_record_end_reason");
  }

  @Test
  @DisplayName("检查约束：注销的两个结束原因（发起、到期执行）能写入")
  void should_end_records_with_deletion_reasons() {
    User user =
        users.save(
            User.register(
                EmailAddress.of("ck.deletion@example.com"), Username.of("u-ck-deletion")));

    for (LoginEndReason reason :
        List.of(LoginEndReason.DELETION_REQUESTED, LoginEndReason.DELETION_COMPLETED)) {
      insertRaw(user.getId(), "WEB", EXPIRES_AT, reason.name());
    }

    assertThat(
            jdbcTemplate.queryForList(
                "select end_reason from idn_user_login_record where user_id = ? order by id",
                String.class,
                user.getId()))
        .containsExactly("DELETION_REQUESTED", "DELETION_COMPLETED");
  }

  @Test
  @DisplayName("检查约束：第 9.2 节的八个结束原因都能写入")
  void should_accept_all_end_reasons() {
    User user =
        users.save(
            User.register(EmailAddress.of("ck.reasons@example.com"), Username.of("u-ck-reasons")));

    for (String reason :
        List.of(
            "LOGOUT",
            "BANNED",
            "REPLACED",
            "REVOKED",
            "EMAIL_VERIFIED",
            "PASSWORD_CHANGED",
            "PASSWORD_RESET",
            "EMAIL_REVERTED")) {
      insertRaw(user.getId(), "WEB", EXPIRES_AT, reason);
    }
  }

  @Test
  @DisplayName("检查约束：结束时间和结束原因要么都有要么都没有")
  void should_require_end_fields_together() {
    User user =
        users.save(User.register(EmailAddress.of("ck.pair@example.com"), Username.of("u-ck-pair")));

    assertThatThrownBy(() -> insertRaw(user.getId(), "WEB", EXPIRES_AT, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_login_record_ended");
  }

  @Test
  @DisplayName("设备与来源的五列存进去、读出来一致")
  void should_round_trip_device_and_origin_columns() {
    User user =
        users.save(
            User.register(EmailAddress.of("origin.record@example.com"), Username.of("u-origin")));
    ClientOrigin origin =
        ClientOrigin.builder()
            .ip("2001:4860:4860::8888")
            .userAgent("Mozilla/5.0 (Macintosh) Chrome/141.0.0.0")
            .deviceName("Chrome 141 · macOS")
            .deviceClass(DeviceClass.DESKTOP)
            .ipRegion("美国")
            .build();

    UserLoginRecord saved =
        records.save(
            UserLoginRecord.start(user.getId(), LoginClient.of("web", DEVICE), origin, EXPIRES_AT));

    UserLoginRecord reloaded = records.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getUserAgent()).isEqualTo("Mozilla/5.0 (Macintosh) Chrome/141.0.0.0");
    assertThat(reloaded.getDeviceName()).isEqualTo("Chrome 141 · macOS");
    assertThat(reloaded.getDeviceClass()).isEqualTo(DeviceClass.DESKTOP);
    assertThat(reloaded.getClientIp()).isEqualTo("2001:4860:4860::8888");
    assertThat(reloaded.getIpRegion()).isEqualTo("美国");
  }

  @Test
  @DisplayName("检查约束：端只能是 DESKTOP、MOBILE、TABLET、UNKNOWN")
  void should_reject_unknown_device_class() {
    User user =
        users.save(
            User.register(EmailAddress.of("ck.class@example.com"), Username.of("u-ck-class")));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO idn_user_login_record (id, user_id, client_type, device_class,"
                        + " expires_at) VALUES (?, ?, 'WEB', 'PHONE', ?)",
                    SnowflakeIdGenerator.getId(),
                    user.getId(),
                    Timestamp.from(EXPIRES_AT)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_idn_user_login_record_device_class");
  }

  /// 绕过聚合直接插一行，用来触发检查约束。
  ///
  /// @param userId 用户 ID
  /// @param clientType 客户端类型
  /// @param endedAt 结束时间，可为 `null`
  /// @param endReason 结束原因，可为 `null`
  private void insertRaw(long userId, String clientType, Instant endedAt, String endReason) {
    jdbcTemplate.update(
        "INSERT INTO idn_user_login_record (id, user_id, client_type, device_class, expires_at,"
            + " ended_at, end_reason) VALUES (?, ?, ?, 'UNKNOWN', ?, ?, ?)",
        SnowflakeIdGenerator.getId(),
        userId,
        clientType,
        Timestamp.from(EXPIRES_AT),
        endedAt == null ? null : Timestamp.from(endedAt),
        endReason);
  }

  @Test
  @DisplayName("用过期的版本保存时转成 UserModifiedConcurrentlyException，文案不带实体名")
  void should_translate_stale_version_to_conflict() {
    User user =
        users.save(
            User.register(
                EmailAddress.of("stale.record@example.com"), Username.of("u-stale-record")));
    UserLoginRecord saved =
        records.save(
            UserLoginRecord.start(user.getId(), LoginClient.web(), UNKNOWN_ORIGIN, EXPIRES_AT));
    UserLoginRecord stale = records.findById(saved.getId()).orElseThrow();
    saved.end(LoginEndReason.REPLACED, Instant.parse("2026-10-09T09:00:00Z"));
    records.save(saved);

    stale.end(LoginEndReason.LOGOUT, Instant.parse("2026-10-09T09:00:01Z"));

    assertThatThrownBy(() -> records.save(stale))
        .isInstanceOf(UserModifiedConcurrentlyException.class)
        .hasMessage("用户正被其他操作修改，请重试");
  }

  @Test
  @DisplayName("按 ID 批量查：只返回存在的")
  void should_find_records_by_ids() {
    long userId = newUser("by.ids");
    UserLoginRecord first = records.save(start(userId, DEVICE, EXPIRES_AT));
    UserLoginRecord second = records.save(start(userId, null, EXPIRES_AT));

    assertThat(records.findAllByIds(List.of(first.getId(), second.getId(), 1L)))
        .extracting(UserLoginRecord::getId)
        .containsExactlyInAnyOrder(first.getId(), second.getId());
    assertThat(records.findAllByIds(List.of())).isEmpty();
  }

  @Test
  @DisplayName("见过这台设备：同账号同设备、结束或过期时刻在窗口内、不是被踢掉的")
  void should_tell_whether_device_was_seen() {
    Instant since = Instant.parse("2026-10-01T00:00:00Z");
    DeviceId device = new DeviceId(DEVICE);
    long open = newUser("seen.open");
    records.save(start(open, DEVICE, EXPIRES_AT));
    long stale = newUser("seen.stale");
    records.save(ended(stale, Instant.parse("2026-09-30T23:59:59Z"), LoginEndReason.LOGOUT));
    long kicked = newUser("seen.kicked");
    records.save(ended(kicked, Instant.parse("2026-10-10T00:00:00Z"), LoginEndReason.REVOKED));
    long changed = newUser("seen.changed");
    records.save(
        ended(changed, Instant.parse("2026-10-10T00:00:00Z"), LoginEndReason.PASSWORD_CHANGED));
    long stranger = newUser("seen.stranger");

    assertThat(records.existsTrustedDevice(open, device, since)).isTrue();
    assertThat(records.existsTrustedDevice(stale, device, since)).isFalse();
    assertThat(records.existsTrustedDevice(kicked, device, since)).isFalse();
    assertThat(records.existsTrustedDevice(changed, device, since)).isTrue();
    assertThat(records.existsTrustedDevice(stranger, device, since)).isFalse();
  }

  @Test
  @DisplayName("踢掉一台设备后，它更早的记录（登出、静默过期）也不再算见过；踢掉之后再登录的记录才算")
  void should_distrust_earlier_records_of_a_kicked_device() {
    Instant since = Instant.parse("2026-10-01T00:00:00Z");
    DeviceId device = new DeviceId(DEVICE);
    long userId = newUser("seen.kicked.earlier");
    records.save(ended(userId, Instant.parse("2026-10-05T00:00:00Z"), LoginEndReason.LOGOUT));
    records.save(start(userId, DEVICE, EXPIRES_AT));
    records.save(ended(userId, Instant.parse("2026-10-10T00:00:00Z"), LoginEndReason.REVOKED));

    assertThat(records.existsTrustedDevice(userId, device, since)).isFalse();

    records.save(start(userId, DEVICE, EXPIRES_AT));

    assertThat(records.existsTrustedDevice(userId, device, since)).isTrue();
  }

  @Test
  @DisplayName("按退役时刻批量删：已结束看结束时刻，未结束看绝对过期，分批返回删除数")
  void should_delete_retired_records_in_batches() {
    Instant cutoff = Instant.parse("2026-06-01T00:00:00Z");
    long userId = newUser("retire");
    UserLoginRecord endedLongAgo =
        records.save(ended(userId, Instant.parse("2026-01-01T00:00:00Z"), LoginEndReason.LOGOUT));
    UserLoginRecord expiredLongAgo =
        records.save(start(userId, null, Instant.parse("2026-03-01T00:00:00Z")));
    UserLoginRecord expiredJustBefore =
        records.save(start(userId, null, Instant.parse("2026-05-31T23:59:59Z")));
    UserLoginRecord alive = records.save(start(userId, null, EXPIRES_AT));
    UserLoginRecord endedRecently =
        records.save(ended(userId, Instant.parse("2026-06-01T00:00:00Z"), LoginEndReason.LOGOUT));

    assertThat(records.deleteRetiredBefore(cutoff, 2)).isEqualTo(2);
    assertThat(records.deleteRetiredBefore(cutoff, 2)).isEqualTo(1);
    assertThat(records.deleteRetiredBefore(cutoff, 2)).isZero();

    assertThat(
            records.findAllByIds(
                List.of(
                    endedLongAgo.getId(),
                    expiredLongAgo.getId(),
                    expiredJustBefore.getId(),
                    alive.getId(),
                    endedRecently.getId())))
        .extracting(UserLoginRecord::getId)
        .containsExactlyInAnyOrder(alive.getId(), endedRecently.getId());
  }

  /// 建一个用户。
  ///
  /// @param name 邮箱本地部分，也用于用户名
  /// @return 用户 ID
  private long newUser(String name) {
    return users
        .save(
            User.register(
                EmailAddress.of(name + "@example.com"), Username.of("u-" + name.replace('.', '-'))))
        .getId();
  }

  /// 一条未结束的记录。
  ///
  /// @param userId 用户 ID
  /// @param device 设备标识，可以为 `null`
  /// @param expiresAt 绝对过期时间
  /// @return 记录
  private static UserLoginRecord start(long userId, String device, Instant expiresAt) {
    return UserLoginRecord.start(userId, LoginClient.of("web", device), UNKNOWN_ORIGIN, expiresAt);
  }

  /// 一条带设备标识、已结束的记录（绝对过期在很久以后）。
  ///
  /// @param userId 用户 ID
  /// @param endedAt 结束时间
  /// @param reason 结束原因
  /// @return 记录
  private static UserLoginRecord ended(long userId, Instant endedAt, LoginEndReason reason) {
    UserLoginRecord login = start(userId, DEVICE, EXPIRES_AT);
    login.end(reason, endedAt);
    return login;
  }
}
